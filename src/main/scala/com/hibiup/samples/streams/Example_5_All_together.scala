package com.hibiup.samples.streams

import akka.stream.Materializer

object Example_5_All_together {
    /**
      * 一个比较完整的 integration 的例子演示了 GraphDSL, Integration, BackPressure，和 Cats State Monad 的组合用法：
      *
      * 简单流程：
      *   假设用户登录（credential.userid:Int）=>
      *     设置登录时间(State Monad) =>
      *       获取用户数据(UserInfo) =>
      *         返回用户登录信息（String）=>
      *           打印（有待改进为 IO Monad）
      * */
    def integration_with_graph() = {
        import java.util.Date
        import scala.concurrent.Future

        import cats.data.State
        import akka.actor.{Actor, ActorSystem, Props}
        import akka.stream.scaladsl.{Flow, Sink, Source}
        import akka.stream.ActorMaterializer
        import akka.pattern.ask
        import akka.util.Timeout

        /***********************************************************************************
          * 1) 数据和运行环境准备
          *
          * 1-1) 定义数据类型
          * */
        final case class Credential(userId: Int,state: Option[Date])
        final case class UserInfo(id:Int, first:String, last:String, lastLogin:Date)

        implicit val ec =  scala.concurrent.ExecutionContext.global
        import concurrent.duration._


        /***********************************************************************************
          * 2) 构建 Actor system，它包含了大部分业务处理过程
          *
          *   * 注意1：Actor的返回
          *   * 注意2：Future 和 State Monad 的使用
          * */
        implicit val sys = ActorSystem("test-system")

        /**
          * 2-1) 定义 Login Actor。这里利用到 Cats State Monad
          * */
        val loginStateMonad = State[Credential, Date] { c =>
            val lastLogin = if (c.userId % 5 == 0) null else new Date
            (Credential(c.userId, Option(lastLogin)), lastLogin)
        }
        class LoginActor extends Actor {
            override def receive: Receive = {
                case c:Credential =>
                    sender() ! loginStateMonad.run(c).value._1  // 调用 State monad
            }
        }
        val loginActorRef = sys.actorOf(Props(new LoginActor), name="loginActor")

        /**
          * 2-2) 定义 LoginState => UserInfo。用到 Future
          * */
        def getUserInfo(c:Credential) =  Future {c match {    // 用到 Future Monad
            case Credential(id, Some(d)) => Option(UserInfo(id, s"First_$id", s"Last_$id", d))
            case _ => Option(null)
        } }
        class UserInfoActor extends Actor {
            override def receive: Receive = {
                case c:Credential => {
                    val from = sender()
                    getUserInfo(c).map(from ! _)              // 在 future 线程中返回数据
                }
            }
        }
        val userInfoActorRef = sys.actorOf(Props(new UserInfoActor))

        /**
          * 2-3) 定义 UserInfo => UserStateInfo:String Actor
          * */
        class UserStateActor extends Actor {
            override def receive: Receive = {
                case Some(u:UserInfo) =>
                    sender() ! s"Last login date for user: '${u.first}, ${u.last}' is ${u.lastLogin}"
                case _ =>
                    sender() ! s"User has not been found!"
            }
        }
        val userStateInfoActorRef = sys.actorOf(Props(new UserStateActor))


        /***********************************************************************************
          * 3) 定义 Stream 的各个环节
          *
          *   * 注意1：backpression 的设置和对速度的影响
          *   * 注意2：Stream 各环节返回值相匹配
          **/
        import akka.stream.OverflowStrategy

        val delay = 1000                                       // 人为设置处理延迟 10s
        implicit val ask_timeout = Timeout(delay*10 seconds)   // 缺省 actor ask timeout
        val numOfParallelism = 3                               // 并发请求数

        /**
          * 3-1) 模拟一个数据源(Stream 类型)
          *
          * buffer size。 这里设置的其实是最小 size。参考：https://doc.akka.io/docs/akka/2.5/stream/stream-rate.html
          * */
        val bufferSize = 1
        val overflowStrategy = OverflowStrategy.backpressure     // 定义 backPressure 策略为 backPressure，即要求上游减速
        def many_many_users(number:Int):LazyList[Credential] = {
            println(s"  source => $number: ${new Date}")         // <-- 观察发送速度
            if (number>1) Credential(number, None) #:: many_many_users(number-1)    // 用 #:: 构建 Stream 流
            else LazyList(Credential(number, None))
        }
        /**
          * 设置 buffer size 和 overflow strategy。
          *
          * 需要特别注意，这里的设置影响该环节本身的速度，如本例我们将 f1 的 buffer=100，而下一级 f2 的 buffer=1，那么我们将看
          * 到 Source~>f1 很快走完(沉淀在 f1 的 buffer 中)，然后 f2 和 f3 因为下游的阻塞和本身 buffer 的限制而明显变慢。
          * */
        val source = Source[Credential](many_many_users(50))
            .buffer(bufferSize, overflowStrategy)

        /**
          * 3-2) 定义流程中各个处理环节
          * */
        val f1 = Flow[Credential]
            /** 允许并发处理 */
            .mapAsync(parallelism = numOfParallelism)(c => {
            println(s"  f1 => ${c.userId}: ${new Date}")      // 观察各级发送速度因为下游的变慢而变慢
            /** 注意：mapTo 到 Flow 的输出类型，这个输出类型和下一个 Flow 的输入类型必须匹配。 */
            (loginActorRef ? c).mapTo[Credential]
        }).buffer(bufferSize * 100, overflowStrategy)          // 100 倍 buffer size

        /** 输入类型是上一个 Flow 的输出类型 */
        val f2 = Flow[Credential].mapAsync(numOfParallelism)(c => {
            println(s"  f2 => ${c.userId}:  ${new Date}")
            // 注意：mapTo 到 Flow 的输出类型 UserInfo
            (userInfoActorRef ? c).mapTo[Option[UserInfo]]
        }).buffer(bufferSize, overflowStrategy)                // 故意限制 buffer=1 制造瓶颈

        // 注意：输入类型 UserInfo，和上一环节一致
        val f3 = Flow[Option[UserInfo]].mapAsync(numOfParallelism)(c => {
            c match {
                case Some(u) => println(s"  f3 => ${u.id}: ${new Date}")
                case _ => println(s"  f3 => unknown: ${new Date}")
            }
            // 输出 String
            (userStateInfoActorRef ? c).mapTo[String]
        }).buffer(bufferSize, overflowStrategy)

        /**
          * 3-3) 定义 Sink
          *
          * 人为降低处理速度，产生回压，会看到 f2 和 f3 的发射速度和 Sink 被迫同步。如果变换策略为 dropX，会看到数据丢失。
          * */
        // 输入 String
        val end = Sink.foreach[String] { r =>
            Thread.sleep(delay)
            println(r)   // 有待改进为 IO Monad
        }


        /***********************************************************************************
          * 4) 用 Graph 组装 Stream
          *
          *   * 注意1：各环节对接的时候的数据类型匹配
          *   * 注意2：分支与合并可能导致的异常
          *   * 注意3：Sink定义在外部或内部对流程定义产生的影响（需要另例观察）
          *
          * */
        import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
        import akka.Done
        import akka.stream.scaladsl.{Broadcast, Merge}
        import akka.stream.ClosedShape
        val g = RunnableGraph.fromGraph(GraphDSL.create(end) { implicit builder: GraphDSL.Builder[Future[Done]] => out =>
            /** Broadcast 的输入类型是上一个 Source 或 Flow 的输出类型
              *
              * 注意，因为本例的流程在实际过程中没有分支和合并，因此注释掉了 broadcast 和 merge，否则会导致以下错误：
              *
              *   Exception: Inlets [Merge.in1] were not returned in the resulting shape and not connected
              *
              * */
            //val broadcast = builder.add(Broadcast[Credential](2))
            /** Merge 的输入类型是上一个 Flow 的输出类型 */
            //val merge = builder.add(Merge[String](2))

            /** 串联起来，注意每个环节之间的输出与输入类型要匹配 */
            import GraphDSL.Implicits._
            source /*~> broadcast*/ ~> f1 ~> f2 ~> f3 /*~> merge*/ ~> out

            ClosedShape
        })

        /***********************************************************************************
          * 5) 执行
          *
          *   * 注意1：buffer 的设置。参考：https://doc.akka.io/docs/akka/2.5/stream/stream-rate.html
          */
        //import akka.stream.ActorMaterializerSettings
        implicit val mat = Materializer(sys)
        import scala.concurrent.Await
        Await.result(g.run(), Duration.Inf)

        sys.terminate()
        Await.result(sys.whenTerminated, Duration.Inf)
    }
}
