package com.hibiup.samples.helloakka

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.{Backoff, BackoffOpts, BackoffSupervisor, ask}
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object Example_4_Exception {
    /** 参考：
      *   https://doc.akka.io/docs/akka/2.5/general/supervision.html
      *   https://hk.saowen.com/a/6eab3a51f5adc2c16663fec0c815dd2ca51cf5908c61f5a7d8957f7d8c03fd9b
      * */
    class ChileActor extends Actor {
        override def receive = {
            /** 模拟异常发生在子系统里 */
            case "Fail" => {
                println(s"${self.path} a message was received")
                throw new RuntimeException()
            }

            case x => println(x)
        }

        /** supervisorStrategy 重启时触发这个方法。*/
        override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
            println(s"${self.path} is restarting by ${reason.getMessage}")
            super.preRestart(reason, message)
        }
        override def postRestart(reason: Throwable): Unit = {
            println(s"${self.path} is restarted by ${reason.getMessage}")
            super.postRestart(reason)
        }
        override def postStop(): Unit = {
            println(s"${self.path} is stopped!")
            super.postStop()
        }
    }

    class RootActor extends Actor {
        //implicit val timeout:Duration = Duration.Inf
        implicit val timeout:Timeout = 60 second
        implicit val ec: ExecutionContext = context.dispatcher

        /** B-1) 定义子系统监控策略：如果失败则重启子系统. OneForOne 策略是只启动失败的这个 Actor。还有一个 AllForOne 策略是
          *      重启所的 Actor，仅用于有强烈依赖关系的 Actor 系统，比如数据库联接 Actor 重启了，所有其它 Actor 都必须重启。
          *      （实际上 BackoffSupervisor 也可以用于子 Actor 监控）
          * */
        override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 60 seconds) {
            case e: Exception => {
                e.printStackTrace()
                Resume  // 忽略错误
            }
        }

        /** supervisorStrategy 重启时触发这个方法。*/
        override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
            println(s"${self.path} is restarting by ${reason.getMessage}")
            super.preRestart(reason, message)
        }
        override def postRestart(reason: Throwable): Unit = {
            println(s"${self.path} is restarted by ${reason.getMessage}")
            super.postRestart(reason)
        }

        /** B-2）正常建立子 Actor */
        override def preStart() : Unit = {
            context.actorOf(Props[ChileActor](), "will-fail-actor")// start the child when supervisor starts
        }

        override def postStop(): Unit = {
            println(s"${self.path} is stopped!")
            super.postStop()
        }

        override def receive = {
            /** 模拟 root actor 发生异常 */
            case "Fail" => {
                println("System gonna failed")
                throw new RuntimeException
            }

            case "Quit" => {
                context.system.terminate()
            }

            case _ => {
                println(s"${self.path} a message was received")
                val childRef = context.actorSelection("will-fail-actor")//context.actorOf(Props[ChileActor], "will-fail-actor")

                /** B-3) 通知子系统产生异常 */
                childRef ! "Fail"

                //Thread.sleep(1000)
                childRef ! "Other"
            }
        }
    }

    def apply() = {
        val system = ActorSystem("my-system")

        implicit val timeout:Timeout = 60 second
        implicit val ec: ExecutionContext = system.dispatcher

        val rootProps  = Props(new RootActor())

        /** A-1 定义 root Actor 的时候同时定义一个 supervisor */
        val supervisor = BackoffSupervisor.props(
            /** 当一个应该永远存在的 Actor 由于某个原因而停止时用 onStop。
              * 当一个 Actor 因为异常而失败时用 onFailure。*/
            BackoffOpts.onFailure(
                /** 在 supervisor 中引用 root props */
                rootProps,
                childName = "root-actor",
                minBackoff = 3.seconds,
                maxBackoff = 30.seconds,
                randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
            ).withAutoReset(10.seconds) // reset if the child does not throw any errors within 10 seconds
                    .withSupervisorStrategy(
                OneForOneStrategy() {
                    case e: Exception => {
                        e.printStackTrace()
                        Resume  // 忽略错误
                    }
                    case _ => Escalate
                }))

        /** A-2 通过 supervisor 启动 root Actor */
        val rootRef = system.actorOf(supervisor, name = "echoSupervisor")
        rootRef ! "Start"

        Thread.sleep(1000)
        /** A-3 通知 root 产生异常 */
        rootRef ! "Fail"

        Thread.sleep(1000)
        rootRef ! "Start"

        /** 退出 Actor system */
        rootRef ! "Quit"

        // 收尾清理
        system.whenTerminated.onComplete{
            case x => ??? // TODO: 结束进程
        }

        Await.result(system.whenTerminated, Duration.Inf)
    }
}
