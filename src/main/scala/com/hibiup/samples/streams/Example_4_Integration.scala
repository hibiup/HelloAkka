package com.hibiup.samples.streams.sample_4

import akka.stream.Materializer

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
        implicit val mat = Materializer(sys)

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

        sys.terminate()
        Await.result(sys.whenTerminated, Duration.Inf)
    }
}
