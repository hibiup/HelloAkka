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
        import cats.Eval

        /**
          * 1) 数据和运行环境准备
          *
          * 1-1) 定义数据类型
          * */
        final case class Credential(userId: Int,state: Option[Date])
        final case class UserInfo(id:Int, first:String, last:String, lastLogin:Date)

        /**
          * 2) 构建 Actor system，它包含了大部分业务处理过程
          *
          * 1-2) 新建 Akka system 并得到 Stream 的物化器
          * */
        implicit val sys = ActorSystem("test-system")
        implicit val mat = ActorMaterializer()

        def define_akka_system() = {
            /** 定义 Login Actor */
            val loginStateMonad = State[Credential, Date] { c =>
                val lastLogin = if (c.userId % 5 == 0) null else new Date
                (Credential(c.userId, Option(lastLogin)), lastLogin)
            }
            class LoginActor extends Actor {
                override def receive: Receive = {
                    case c:Credential => sender ! loginStateMonad.run(c).value
                }
            }
            val loginActor = sys.actorOf(Props(new LoginActor), name="loginActor")

            /** 定义 LoginState => UserInfo 隐式转换和 Actor */
            implicit val ec =  scala.concurrent.ExecutionContext.global
            def getUserInfo(c:Credential, d:Date) = Future{
                c match {
                    case Credential(id, Some(d)) => Option(UserInfo(id, s"First_$id", s"Last_$id", d))
                    case _ => Option(null)
                }
            }
            class UserInfoActor extends Actor {
                override def receive: Receive = {
                    case e:(Credential,Date) => {
                        val from = sender
                        getUserInfo(e._1, e._2).onComplete(_ => from ! _)
                    }
                }
            }
            val userInfoActor = sys.actorOf(Props(new UserInfoActor))

            /** 定义 UserInfo => UserStateInfo:String Actor */
            class UserStateActor extends Actor {
                override def receive: Receive = {
                    case u:UserInfo => sender ! s"Last login date for ${u.first} ${u.last} is ${u.lastLogin}"
                }
            }
            val userStateInfoActor = sys.actorOf(Props(new UserStateActor))

            (loginActor, userInfoActor, userStateInfoActor)
        }
        val (loginActorRef, userInfoActorRef, userStateInfoActorRef) = define_akka_system()

        /** 3) 定义 Stream 的各个环节
          *
          * 3-1) 定义数据源(模拟一个 Stream) */
        def manymanyusers(number:Int):Stream[Credential] = {
            if (number>1) Credential(number, None) #:: manymanyusers(number-1)
            else Stream(Credential(number, None))
        }
        val source = Source[Credential](manymanyusers(100))

        /** 3-2) 定义处理 */
        import concurrent.duration._
        implicit val timeout = Timeout(10 seconds)
        val f1 = Flow[Credential].mapAsync(parallelism = 3)(c =>
            loginActorRef ? c
        )  // Eval[(Credential, Boolean)]

        val f2 = Flow[Eval[(Credential, Boolean)]].mapAsync(parallelism = 3)(c =>
            userInfoActorRef ? c
        )  // UserInfo

        /** 3-3) 定义 Sink */
        // ...

        /**
          * 4) 用 Graph 组装 Stream
          * */
    }
}
