package com.hibiup.samples.helloakka

/**
  * Created by 326487162 on 2018/03/16.
  */

import akka.actor._
import akka.routing.{ActorRefRoutee, RoundRobinPool, RoundRobinRoutingLogic, Router}

import scala.concurrent.Await
import scala.concurrent.duration._

//import akka.routing.RoundRobinRouter
//import akka.util.duration._

/**
  * 主程序
  */
object Pi extends App {
  // Message classes
  sealed trait PiMessage
  case object Calculate extends PiMessage
  case class Work(start: Int, nrOfElements: Int) extends PiMessage
  case class Result(value: Double) extends PiMessage
  case class PiApproximation(pi: Double, duration: Duration)

  // Worker actor
  class Worker extends Actor {
    // Worker 的接收消息接口。
    def receive = {
      case Work(start, nrOfElements) =>
        // sender 定义在 Actor 中，由 implicit 方法绑定到消息的发起者，在这里指向 Master。
        // Result 接受 calculatePiFor() 的返回值，并将它返回给 sender
        sender() ! Result(calculatePiFor(start, nrOfElements)) // perform the work
    }

    // calculatePiFor ...
    def calculatePiFor(start: Int, nrOfElements: Int): Double = {
      var acc = 0.0
      for (i <- start until (start + nrOfElements))
         /**
           * (1 - (i % 2) * 2) 得到正负1
           * i 是因子的偏移量。例如：
           * i=0:  1
           * i=1:  -1/3
           * i=2:  1/5
           * ...
           * 最后求各项的和。
           * 参数 start, nrOfElements 决定了该每段(chunk)的起点，和包含多少个bit，这个例子中最小我们可以调整步长为 1
           */
        // i=0
        acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
      acc
    }
  }

  /**
    * nrOfWorkers – 启动多少个 worker actor
    * nrOfMessages – 计算多少个单元(chunk)
    * nrOfElements – 每个单元(chunk)计算多少个 bit
    * 所以总长度 = nrOfMessages x nrOfElements
    * 每个actor将会被调用的次数 = 总长度/nrOfWorkers
    */
  class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef)
    extends Actor {

    var pi: Double = _
    var nrOfResults: Int = _
    val start: Long = System.currentTimeMillis

    /**
      * 通过 Router 来创建 Worker Actor:
      * Router 是 akka 内建的一种 actor，它将消息从一个actor传给另一个actor
      * 关于Router 参考：https://doc.akka.io/docs/akka/2.2.3/scala/routing.html
      *
      * Props 类称为 routee，它用来建立并管理目标 actor，接受参数是目标 actor class，这里就是 Worker
      * Props.withRouter() 方法为 routee 提供生成与管理 actor 的策略
      * RoundRobinPool 表示以闭循环方式轮讯若干个 actor
      * context.actorOf(routee) 得到 actor(列表代理)
      */
    //val workerRouter = context.actorOf(
    //  Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")
    val workerRouter = context.actorOf(
      RoundRobinPool(nrOfWorkers).props(Props[Worker]()), "workerRouter"
    )

    def receive = {
      case Calculate =>
        // Master 接收到开始计算（Calculate）命令：.
        for (i <- 0 until nrOfMessages)
          // 根据调用的次序，计算出每个 worker 负责的 chunk 偏移量和计算长度，初始化 work消息
          // 然后将消息依据 RoundRobinRouter 策略依次循环传给 workerRouter 代理。
          workerRouter ! Work(i * nrOfElements, nrOfElements)
      case Result(value) =>
        // 接受到来自某个 Worker 的返回消息：
        pi += value         // 累加返回的结果
        nrOfResults += 1    // 累计返回的数量
        if (nrOfResults == nrOfMessages) {    // 如果所有的 worker actor 都已经返回
          // 将最终结果返回给 listener
          listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)
          // 结束 supervisor(Master 自己)。
          context.stop(self)
        }
    }
  }

  /**
    * 主监听进程
    */
  class Listener extends Actor {
    def receive = {
      case PiApproximation(pi, duration) =>
        println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
          .format(pi, duration))

        // 退出程序
        Await.result(context.system.terminate(), 10 second)
    }
  }

  // 主入口
  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) = {
    // 建立并初始化 akka
    val system = ActorSystem("PiSystem")

    // 建立主监听 actor
    val listener = system.actorOf(Props[Listener](), name = "listener")

    // 建立 master actor
    val master = system.actorOf(
      Props(
        new Master(nrOfWorkers, nrOfMessages, nrOfElements, listener)
      ),
      name = "master")

    // 向 master 发出执行计算指令
    master ! Calculate
  }

  // 执行计算。设置10000个片段，每个片段执行10000个单元，由4个actor分担工作。
  calculate(nrOfWorkers = 4, nrOfElements = 10000, nrOfMessages = 10000)
}