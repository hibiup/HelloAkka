package com.hibiup.samples.helloakka

import java.util.Calendar

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, DeadLetter, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router, RoutingLogic}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * 这个例子演示在通道压力大（mailbox capacity 只有 1）的条件下的三种快速处理消息的方案：
  *
  *   1）多个 ChildActor 实例（通过 RoundRobinRoutingLogic 路由策略）每个单线程模式。
  *      测试时通过 AddRouter 的 instances 参数控制实例数量，注销 ChildActor 中的 Future
  *
  *   2）单个 ChildActor 多线程模式
  *      测试时将 AddRouter 的 instances 参数设为 1，打开 ChildActor 中的 Future
  *
  * 并发 16 个请求，由于 CPU 内核只有 8 个，因此以上两种模式一次都只能处理 8 条消息，分两批次处理完成。但是第 2 个方案出现消息溢出，
  * 而多 ChildActor 方案这个现象较缓和（偶尔出现）。适当扩大mailbox 的容量后两者处理速度一样。
  *
  * 但是如果加大线程池数量到 16 个（或 ChildActor 加大到 16 个），处理并不会因为 CPU 数量限制而减慢，16 个请求都可以一次处理完。
  * 多 Actor 并没有表现出比单 Actor多线程更大优势。
  *
  * 结论：从协程角度讲，多 Actor 模式并不比 Future 更有优势（因为 Future 本身就是协程？）但是它自身可能有消息缓存，因此能够缓解通
  * 达压力。线程调度发生在操作系统内核，用户线程并不需要特别考虑线程间的调度，Akka 或 Future 带来的优点是提供了协程调度的能力。
  *
  *   3）通过 Watcher Actor 来监控 ChildActor，发现消息溢出就发送反向背压或增加 ChildActor。这属于溢出后的补救方案，与以上两种
  *      方案可以并用。
  *
  * */
object Example_6_Backpressure extends App{
    lazy val akkaSystemConfiguration = ConfigFactory.parseString(
        """
          |akka.actor.default-mailbox {
          |  mailbox-type = "akka.dispatch.UnboundedMailbox"
          |}
          |
          |akka.actor.bounded-mailbox {
          |  mailbox-type = "akka.dispatch.BoundedMailbox"
          |  mailbox-capacity = 1
          |  mailbox-push-timeout-time = 100ms
          |}
          |
          |akka.actor.default-dispatcher {
          |  type = Dispatcher
          |  throughput = 100
          |  executor = "fork-join-executor"
          |
          |  fork-join-executor {
          |    parallelism-min = 1
          |    parallelism-factor = 1    # 乘上CPU数量得到总的可用线程数
          |    parallelism-max = 8
          |  }
          |}
        """.stripMargin)

    final case class PayLoad[T](msg:T)
    final case class Shutdown()

    object RouterActor {
        def apply(childProps: => Props, instance:Int = 1, rl: RoutingLogic = RoundRobinRoutingLogic() ) = {
            Props(new RouterActor(childProps, instance, rl))
        }
    }

    /** Router 也是一个 Actor*/
    class RouterActor(childProps: => Props, instance:Int, rl: RoutingLogic ) extends Actor with ActorLogging {
        override def preStart() = log.debug(s"${self.path}: Pre-Start")
        override def postStop() = log.debug(s"${self.path}: Post-Stop")
        /**
          * 通过 Router 来生成 Router Actor
          *
          * 参数：
          *    RoutingLogic  路由策略
          *    Vector        代理多少个 Actor
          *   */
        var router = Router(rl, Vector.fill(instance) {
            val actor = context.actorOf(childProps)    // 添加被代理的 actor
            addWatcher(actor)                          // 给 actor 添加 watcher
            ActorRefRoutee(actor)                      // 将被代理的 actor 转成 Routee，并加入队列
        })

        def addWatcher(actor:ActorRef): Unit = {
            val watcher = context.actorOf(Props(classOf[Watcher], actor))       // 为 actor 设置 watcher
            context.system.eventStream.subscribe(watcher, classOf[DeadLetter])  // 将 watcher 添加进事件流，并设置监控消息对象
        }

        def receive: Actor.Receive = {
            case t:Shutdown ⇒
                /** 逐个停止被代理的 actor */
                router.routees.foreach { r =>
                    context.stop(r.asInstanceOf[ActorRefRoutee].ref)
                    router.removeRoutee(r)
                }
                context.system.terminate()
            case p:PayLoad[_] ⇒
                /** Router 不做什么事，除了路由转发 */
                log.debug(s"${self.path}: route to child actor")
                router.route(p, sender())
        }
    }

    /** 监控 ChildActor 的 Watcher 也是个 Actor */
    class Watcher(target: ActorRef) extends Actor with ActorLogging {
        private val targetPath = target.path

        override def preStart() {
            context watch target   // 注册被监控 Actor
        }

        def receive: Actor.Receive = {
            case d: DeadLetter =>
                if(d.recipient.path.equals(targetPath)) {
                    log.info(s"Timed out message: ${d.message.toString}")
                    // TODO: 向上游发送背压消息或增加 ChildActor
                }
        }
    }

    object ChildActor{
        def apply() = Props[ChildActor]
    }

    /** 工作 Actor */
    class ChildActor() extends Actor with ActorLogging {
        override def preStart() = log.debug(s"${self.path}: Pre-Start")
        override def postStop() = log.debug(s"${self.path}: Post-Stop")

        override def receive: Receive = {
            case msg => {
                Future {
                    println(s"${Calendar.getInstance.getTimeInMillis} - [Thread-${Thread.currentThread.getId}] - ${self.path}: $msg")
                    Thread.sleep(1000)
                }(context.dispatcher)
            }
        }
    }

    object BackPressureExample {
        def apply(): Unit = {
            val system = ActorSystem("testSystem", akkaSystemConfiguration)
            val rootRef = system.actorOf(
                RouterActor( ChildActor().withMailbox("akka.actor.bounded-mailbox"), instance = 1), "actor-router"
            )
            rootRef ! PayLoad("Hello-1!")
            rootRef ! PayLoad("Hello-2!")
            rootRef ! PayLoad("Hello-3!")
            rootRef ! PayLoad("Hello-4!")
            rootRef ! PayLoad("Hello-5!")
            rootRef ! PayLoad("Hello-6!")
            rootRef ! PayLoad("Hello-7!")
            rootRef ! PayLoad("Hello-8!")
            rootRef ! PayLoad("Hello-9!")
            rootRef ! PayLoad("Hello-10!")
            rootRef ! PayLoad("Hello-11!")
            rootRef ! PayLoad("Hello-12!")
            rootRef ! PayLoad("Hello-13!")
            rootRef ! PayLoad("Hello-14!")
            rootRef ! PayLoad("Hello-15!")
            rootRef ! PayLoad("Hello-16!")
            Thread.sleep(5000)
            rootRef ! new Shutdown
            Await.result(system.terminate(), 10 second)
        }
    }

    BackPressureExample()
}
