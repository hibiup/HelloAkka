package com.hibiup.samples.streams.sample_4

/**
  * 在现实应用中 akka-stream 往往需要集成其它的外部系统形成完整的应用。这些外部系统可能是 akka 系列系统或者其它类型的系统。
  * 所以，akka-stream 提供了 mapAsync + ask 模式用来从一个运算中的数据流向外连接某个 Actor 来进行数据交换。那么就可以充分利用
  * actor 模式的 routing,cluster,supervison 等等功能来实现分布式高效安全的运算。
  *
  * mapAsync把一个函数f: Out=>Future[T] 在 parallelism 个 Future 里并行运算。
  * */


object Integration_Examples {
    def simple_integration() = {
        import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
        import akka.stream.scaladsl.{Flow, Sink, Source}
        import akka.stream.ActorMaterializer
        import akka.pattern.ask
        import akka.routing.RoundRobinGroup
        import akka.util.Timeout

        import scala.concurrent.Await
        import scala.concurrent.duration._

        /** 1) 定义 Actor 之间的数据包 */
        case class Package(id: Int, msg: String)

        /** 2) 定义 Actor 生成它的 Props */
        class ClientActor extends Actor with ActorLogging {
            override def receive: Receive = {
                case Package(id, msg) =>
                    println(s"${self.path}: receive: [$id]  $msg")
                    val reply = s"${self.path} reply: [$id] ${msg.toUpperCase}"
                    sender() ! reply    // 必须回复 mapAsync, 抵消 backpressure
            }
        }
        def props = Props(new ClientActor)

        /** 3) 新建 Akka system */
        implicit val sys = ActorSystem("demo-system")
        /** 4) 得到 Stream 的物化器 */
        implicit val mat = ActorMaterializer()

        /** 5) 定义一个 ActorRef 池，初始化 3 个 Actor*/
        val numOfActors = 3
        val routee: List[ActorRef] = List.fill(numOfActors)(sys.actorOf(props))
        val routeePaths: List[String] = routee.map{ref => "/user/"+ref.path.name}
        val clientActorPool = sys.actorOf(RoundRobinGroup(routeePaths).props())

        /** 6) 定义 Source */
        val in = Source[Package](
            Package(1, "Hello".toLowerCase())
                    :: Package(2,"World".toLowerCase())
                    :: Package(3,"World1".toLowerCase())
                    :: Package(4,"World2".toLowerCase())
                    :: Package(5,"World3".toLowerCase())
                    :: Package(6,"World4".toLowerCase())
                    :: Nil
        )

        implicit val askTimeout = Timeout(5.seconds)

        /** 7) 定义过程 */
        val flow = Flow[Package]
            /** 7-1) 将处理通过　mapAsync 发送到远程Actor去处理，ask 返回结果 */
            .mapAsync(parallelism = numOfActors)(elem =>
                ask(clientActorPool, elem)
            .mapTo[String])    // Actor send back String.

        /** 8) 定义结果　*/
        val sink = Sink.foreach(println)

        /** 9) 将过程组装起来 */
        val result = in.via(flow).runWith(sink)

        // 被动等待结束（option）
        result.onComplete{
            case x => ??? // TODO: 结束进程
        }(sys.dispatcher)

        // 主动等待结束
        Await.result(result, 10 seconds)
    }

    /**
      * 用 Cats 来处理数据的例子
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

        /**
          * 1) 数据和运行环境准备
          *
          * 1-1) 定义数据类型
          * */
        final case class Credential(userId: Int,state: Option[Date])
        final case class UserInfo(id:Int, first:String, last:String, lastLogin:Date)

        implicit val ec =  scala.concurrent.ExecutionContext.global

        /***********************************************************************************
          * 2) 构建 Actor system，它包含了大部分业务处理过程
          *
          * 1-2) 新建 Akka system 并得到 Stream 的物化器
          * */
        implicit val sys = ActorSystem("test-system")

        def define_akka_system() = {
            /** 定义 Login Actor */
            val loginStateMonad = State[Credential, Date] { c =>
                val lastLogin = if (c.userId % 5 == 0) null else new Date
                (Credential(c.userId, Option(lastLogin)), lastLogin)
            }
            class LoginActor extends Actor {
                override def receive: Receive = {
                    case c:Credential =>
                        sender ! loginStateMonad.run(c).value._1
                }
            }
            val loginActor = sys.actorOf(Props(new LoginActor), name="loginActor")

            /** 定义 LoginState => UserInfo 隐式转换和 Actor */
            def getUserInfo(c:Credential) =  c match {
                case Credential(id, Some(d)) => Option(UserInfo(id, s"First_$id", s"Last_$id", d))
                case _ => Option(null)
            }
            class UserInfoActor extends Actor {
                override def receive: Receive = {
                    case c:Credential => {
                        sender ! getUserInfo(c)
                    }
                }
            }
            val userInfoActor = sys.actorOf(Props(new UserInfoActor))

            /** 定义 UserInfo => UserStateInfo:String Actor */
            class UserStateActor extends Actor {
                override def receive: Receive = {
                    case Some(u:UserInfo) =>
                        sender ! s"Last login date for user: '${u.first}, ${u.last}' is ${u.lastLogin}"
                    case _ =>
                        sender ! s"User has not been found!"
                }
            }
            val userStateInfoActor = sys.actorOf(Props(new UserStateActor))

            (loginActor, userInfoActor, userStateInfoActor)
        }
        val (loginActorRef, userInfoActorRef, userStateInfoActorRef) = define_akka_system()


        /***********************************************************************************
          * 3) 定义 Stream 的各个环节
          *
          * 3-1) 定义数据源(模拟一个 Stream) */
        import akka.stream.OverflowStrategy

        /**
          * buffer size。 这里设置的其实是最小 size。
          * 参考：https://doc.akka.io/docs/akka/2.5/stream/stream-rate.html
          * */
        val bufferSize = 1
        val overflowStrategy = OverflowStrategy.backpressure   // 定义 backpressure 策略
        val numOfActor = 10   // 并发数
        val delay = 1000   // 认为设置处理延迟 10s

        def manymanyusers(number:Int):Stream[Credential] = {
            println(s"  source => $number: ${new Date}")   // <-- 观察发送速度
            if (number>1) Credential(number, None) #:: manymanyusers(number-1)
            else Stream(Credential(number, None))
        }
        val source = Source[Credential](manymanyusers(50))
            /**
              * 设置 buffer size 和 overflow strategy。
              *
              * 需要特别注意，这里的设置影响该环节本身的速度，如本例我们将 Source 的 buffer=100，而下一级 Flow 的 buffer=1，
              * 那么我们将看到 Source 很快发射完全部数据，然后 f1 因为下游的阻塞和它本身 buffer 的限制而明显变慢。
              * */
            .buffer(bufferSize * 100, overflowStrategy)

        /** 3-2) 定义处理 */
        import concurrent.duration._
        implicit val timeout = Timeout(delay*10 seconds)

        val f1 = Flow[Credential]
            /** 允许并发处理 */
            .mapAsync(parallelism = numOfActor)(c => {
            println(s"  f1 => ${c.userId}: ${new Date}")      // 观察各级发送速度因为下游的变慢而变慢
            /** mapTo 到 Flow 的输出类型，这个输出类型和下一个 Flow 的输入类型必须匹配。 */
            (loginActorRef ? c).mapTo[Credential]
        }).buffer(bufferSize, overflowStrategy)               // 设置各级 buffer

        /** 输入类型是上一个 Flow 的输出类型 */
        val f2 = Flow[Credential].mapAsync(numOfActor)(c => {
            println(s"  f2 => ${c.userId}:  ${new Date}")
            (userInfoActorRef ? c).mapTo[Option[UserInfo]]
        }).buffer(bufferSize, overflowStrategy)               // 设置各级 buffer

        val f3 = Flow[Option[UserInfo]].mapAsync(numOfActor)(c => {
            c match {
                case Some(u) => println(s"  f3 => ${u.id}: ${new Date}")
                case _ => println(s"  f3 => unknown: ${new Date}")
            }
            (userStateInfoActorRef ? c).mapTo[String]
        }).buffer(bufferSize, overflowStrategy)               // 设置各级 buffer

        /** 3-3) 定义 Sink */
        val end = Sink.foreach[String] { r =>
            Thread.sleep(delay)                  // <-- 人为降低处理速度，产生回压，应该能够一直回朔到 source 的 buffer 设置。
            println(r)
        }


        /***********************************************************************************
          * 4) 用 Graph 组装 Stream
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

        //import akka.stream.ActorMaterializerSettings
        implicit val mat = ActorMaterializer(/*ActorMaterializerSettings(sys)
            .withInputBuffer(
                initialSize = bufferSize,
                maxSize = bufferSize)*/)
        import scala.concurrent.Await
        Await.result(g.run(), Duration.Inf)
    }
}
