package com.hibiup.samples.helloakka

/**
  * Created by 326487162 on 2018/03/20.
  */
import akka.actor.{ Actor, Props, ActorSystem }
import scala.io.StdIn
import util.control.Breaks._

object ActorHierarchyExperiments extends App {
  val system = ActorSystem("testSystem")  //  新建一个 /user/testSystem 管理树

  // 为新建一个管理树生成根节点. Props 范形根据参数类型返回 actor 实例.
  val rootRef = system.actorOf(Props[SupervisingActor], "first-actor")
  println(s"Root: $rootRef")

  println(">>> Press \"q\" to exit <<<")
  breakable {
    while(true) {
      StdIn.readLine() match {
        case "q" => break()
        case s => rootRef ! s  // 期待输入: new, stop, fail
      }
    }
  }
  system.terminate()  // 终结整个树，这会导致递归终结根节点以下的所有子节点
}

class SupervisingActor extends Actor {
  override def receive: Receive = {
    case "new" ⇒
      // 当根节点收到消息时，通过内建的 context 实例添加一个空子节点
      val childRef = context.actorOf(Props[SupervisedActor], "second-actor")
      println(s"Second: $childRef")
    case msg ⇒ context.child("second-actor").get ! msg
  }
}

class SupervisedActor extends Actor {
  override def receive: Receive = {
    case "fail" =>
      // 模拟失败，actor 失败后会被自动重建。
      println("supervised actor fails now")
      throw new Exception("I failed!")

    case "stop" ⇒
      // actor 主动终结自己的方式。终结之前，它会调用 postStop() 方法
      println(s"I'm killing myself! $self")
      context.stop(self)
  }

  // actor 初创之后，执行之前，会调用该函数
  override def preStart(): Unit = {
    println("child started")
    context.actorOf(Props[GrandChildActor], "second")
  }
  override def postStop(): Unit = println("child stopped")
}

class GrandChildActor extends Actor {
  override def preStart(): Unit = println("grand child started")
  override def postStop(): Unit = println("grand child stopped")

  // Actor.emptyBehavior is a useful placeholder when we don't
  // want to handle any messages in the actor.
  override def receive: Receive = Actor.emptyBehavior
}
