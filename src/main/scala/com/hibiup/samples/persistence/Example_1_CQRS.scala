package com.hibiup.samples.persistence

import akka.actor._
import akka.persistence._

package Example_1_CQRS {

    import org.slf4j.LoggerFactory

    /**
      * A CQRS Example
      * */

    /**
      * 定义命令（写）和事件（读）
      * */
    case class Cmd(data: String)
    case class Event(data: String)

    /**
      * 定义一个 State 用于缓存 event
      * */
    case class ExampleState(events: List[String] = Nil) {
        val logger = LoggerFactory.getLogger(this.getClass)

        def updated(evt: Event): ExampleState = {
            logger.info(s"${evt.data} has been updated to State.")
            copy(evt.data +: events)
        }
        def size: Int = events.length
        override def toString: String = events.reverse.toString
    }

    /**
      * Persistent Actor: 具有以下两个关键方法：
      *
      *   receiveRecover：处理 Event（读） 和 SnapshotOffer 这两类事件。
      *
      *   receiveCommand：用来处理 Command（写入） 和其他命令。
      * */
    class ExamplePersistentActor extends PersistentActor {
        val logger = LoggerFactory.getLogger(this.getClass)

        override def persistenceId = "sample-id-1"   // 每一个 Persistent Actor 都要定义一个唯一 id 标示

        var state = ExampleState()

        /**
          * 处理 Event（读取） 和 SnapshotOffer
          * */
        val receiveRecover: Receive = {
            /** 收到一条新的(写入) Event */
            case event: Event => {
                logger.info(s"receiveRecover: ${event.data} is received.")
                state.updated(event)
            }
            /** */
            case SnapshotOffer(_, snapshot: ExampleState) => {
                logger.info(s"receiveRecover: SnapshotOffer is received.")
                state = snapshot
            }
        }

        /**
          * 处理 Command（写入）
          * */
        val snapShotInterval = 100
        val receiveCommand: Receive = {
            case Cmd(data) =>
                /**
                  * 当要写入数据的时候，通过向 persist （异步）函数传递两个参数来请求：
                  *
                  *   第一个参数（event）是一个 event:A。它包含了要写入的数据和其它信息
                  *
                  *   第二个参数是如何处理这个 Event 的 handler。
                  *
                  * persist 函数是异步的，操作成功的回执会立刻被发送回事件的发送者。如果失败会抛出 onPersistFailure
                  * 或 onPersistRejected 异常。persist 函数的执行如下步骤：
                  * */
                persist(Event(s"$data-${state.size}")) { event =>
                    logger.info(s"receiveCommand#persist: ${event.data} is received.")

                    /** 1) 先将事件缓存下来。*/
                    state.updated(event)

                    /** 2) 然后告知系统的其它可能存在的相关者该事件已经完成。*/
                    context.system.eventStream.publish(event)

                    /** 3) 然后根据预定于的时间间隔将数据异步写入数据库 */
                    if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
                        logger.info(s"receiveCommand#persist: saveSnapshot.")
                        // 保存缓存快照
                        saveSnapshot(state)
                    }
                }
            case "print" => logger.info(s"print state: $state")
        }
    }

    object PersistentActorExample extends App {
        import scala.concurrent.Await
        import scala.concurrent.duration.Duration

        val logger = LoggerFactory.getLogger(this.getClass)

        val system = ActorSystem("example")
        val persistentActor = system.actorOf(Props[ExamplePersistentActor](), "persistentActor-4-scala")

        persistentActor ! Cmd("foo")
        persistentActor ! Cmd("baz")
        persistentActor ! Cmd("bar")
        persistentActor ! "snap"
        persistentActor ! Cmd("buzz")
        persistentActor ! "print"

        logger.info("All command/event has been sent.")

        Thread.sleep(10000)
        system.terminate()
        Await.result(system.whenTerminated, Duration.Inf)
    }
}
