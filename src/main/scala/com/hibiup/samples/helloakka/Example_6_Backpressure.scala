package com.hibiup.samples.helloakka

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router, RoutingLogic}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object Example_6_Backpressure extends App{
    lazy val akkaSystemConfiguration = ConfigFactory.parseString(
        """
          |bounded-mailbox {
          |  mailbox-type = "akka.dispatch.BoundedMailbox"
          |  mailbox-capacity = 10
          |  mailbox-push-timeout-time = 100ms
          |}
        """.stripMargin)

    final case class PayLoad[T](msg:T)
    final case class Terminated()

    object AddRouter {
        def apply(childProps: => Props, instance:Int = 1, rl: RoutingLogic = RoundRobinRoutingLogic() ) = {
            /** Router 也是一个 Actor*/
            class RouterActor extends Actor with ActorLogging {
                override def preStart() = log.info(s"${self.path}: Pre-Start")
                override def postStop() = log.info(s"${self.path}: Post-Stop")

                /**
                  * 通过 Router 来生成 Router Actor
                  *
                  * 参数：
                  *    RoutingLogic  路由策略
                  *    Vector        代理多少个 Actor
                  *   */
                var router = Router(rl, Vector.fill(instance) {
                        val actor = context.actorOf(childProps)    // 添加被代理的 actor
                        context watch actor
                        ActorRefRoutee(actor)                      // 将被代理的 actor 转成 Routee
                    })

                def receive = {
                    case t:Terminated =>
                        /** 逐个停止被代理的 actor */
                        router.routees.foreach { r =>
                            context.stop(r.asInstanceOf[ActorRefRoutee].ref)
                            router.removeRoutee(r)
                        }
                    case p:PayLoad[_] => {
                        /** Router 不做什么事，除了路由转发 */
                        log.info(s"${self.path}: route to child actor")
                        router.route(p, sender())
                        //Thread.sleep(100)
                    }
                }
            }

            Props(new RouterActor)
        }
    }

    class ChildActor extends Actor with ActorLogging {
        override def preStart() = log.info(s"${self.path}: Pre-Start")
        override def postStop() = log.info(s"${self.path}: Post-Stop")

        override def receive: Receive = {
            case msg => println(s"${self.path}: $msg")
        }
    }

    object BackPressureExample {
        def apply(): Unit = {
            val system = ActorSystem("testSystem", akkaSystemConfiguration)
            val rootRef = system.actorOf(AddRouter(Props[ChildActor].withMailbox("bounded-mailbox"), instance = 5), "actor-router")
            rootRef ! PayLoad("Hello-1!")
            rootRef ! PayLoad("Hello-2!")
            rootRef ! PayLoad("Hello-3!")
            rootRef ! PayLoad("Hello-4!")
            rootRef ! PayLoad("Hello-5!")
            rootRef ! PayLoad("Hello-6!")
            rootRef ! PayLoad("Hello-7!")
            rootRef ! new Terminated()

            Thread.sleep(100)
            Await.result(system.terminate(), 10 second)
        }
    }

    BackPressureExample()
}
