package com.hibiup.samples.streams

/**
  *  Akka之Flow相关API总结: https://www.jianshu.com/p/91bf067f1a2a
  * */

object Sample_1 {
    def HelloAkkaStream() = {
        import akka.stream._
        import akka.stream.scaladsl._

        import akka.{ NotUsed, Done }
        import akka.actor.ActorSystem
        import scala.concurrent._

        import java.nio.file.Paths
        import akka.util.ByteString
        import scala.concurrent.duration._

        /** 1）初始化一个 Akka System */
        implicit val system = ActorSystem("testSystem") // 隐式生成 ActorRefFactory 的子类 ActorSystem。

        /** 2）基于 ActorSystem 生成 Akka Stream 的执行引擎。 这个引擎将从数据源中读取数据，并将数据传递给执行函数。 */
        implicit val materializer = ActorMaterializer() // 隐式获得 system。并生成隐式 ActorMeterializer 实例。

        implicit val ec = system.dispatcher    // 隐式生成 ExecutionContext(线程池) 的子类 ExecutionContextExecutor

        /** 3）定义一个数据发射源 Source。
          *
          * 它有两个参数，第一个是数据类型，比如以下这个数据源将产生 1 到 100 整数。
          * 第二个参数任意，可以用于产生一些辅助值，比如可以是数据源的IP地址等相关数据。如果没有可以用 NotUsed
          *
          * Akka Streams 是一个具有两个概念的混合物：流中“流动”的值，以及流之外产生和可见的值。 Akka Stream 只有在它
          * 处于封闭状态下时，也就是说它只有被分配了开始和结束（Source 和 Sink）时才能工作，而 Flow 之外的任何人
          * 都无法窥视它的内部。 如果您所做的只是将数据从 A 点传送到 B 点，那么这就没关系，但是如果您想要这个流产生您
          * 想要查看的值，那么您将无法做到因为你接触不到内部。
          *
          * 如果确实关心 Flow 内部的值，那么参考 Sample_3_Graph
          * */
        val source: Source[Int, NotUsed] = Source(1 to 100)

        /** 4）从数据源中萃取数据并处理。
          *
          * 启动数据源工作流的方法都以 run 开头，该方法隐式从上下文中获得 akka stream 的执行引擎。最终返回一个
          * Future。在本例中执行引擎将持续从 source 中读取数据然后传递给第一个参数，第一个参数是将值打印出来。
          *
          * akka-stream 是由数据源头 Source，流通节点 Flow 和数据流终点 Sink 三个框架性的流构件。Source 和 Sink
          * 是 stream 的两个独立端点，而 Flow 处于 stream Source 和 Sink 中间，可能由 0 到多个通道式的节点组成，
          * 每个节点代表某些数据流元素转化处理功能，它们的链接顺序代表整体作业的流程。
          *
          * 以下例子由 Source 直接到 Sink
          * */
        val done: Future[Done] = source.runWith(Sink.foreach(println)) // 隐式获得 materializer 参数

        /** 4-1) source， flow 和 sink 都是可以重用的，下面定义一个将数据写入文件的处理流程。重用之前的 Source，经由两个
          * flow 处理后结果输出到文件 Sink。(更完整的可重用例子参见：https://doc.akka.io/docs/akka/2.5/stream/stream-quickstart.html)
          *
          * */
        // Flow: scan + zip。 scan 方法类似 fold，初始值是 BitInt(1),然后逐个取出 Source 中的元素 next，得到每一级的阶
        //       乘值 acc，将结果集传给下一级 zip Flow。
        val factorials = source.scan(BigInt(1))((acc, next) ⇒ {/* println(acc, next);*/ acc * next })
            .zipWith(Source(0 to 100))((num, idx) ⇒ s"$idx! = $num")
        // Flow: map
        val accent: Future[IOResult] =
            factorials.map(line ⇒ ByteString(s"$line\r\n"))
                .runWith{
                    // Sink
                    FileIO.toPath(Paths.get("C:\\\\Temp\\numbers.txt"))
                }

        /** 5）等待 Stream 处理结束后关闭 Akka System。
          *
          * 关闭方法由 Future 的 onComplete 回调函数执行，回调函数隐式获得 AkkaSystem 的执行上下文。*/
        val result = Future.sequence(List(done,accent))
        result.onComplete(_ ⇒ { println("Finish!!");system.terminate() }) // 隐式获得 ec

        /** 6) 因为 onComplete 是异步操作，如果是最后一行，任务可能会被迫终止，因此退出前必须等待 result 完成。*/
        Await.result(result, 10 seconds)
    }
}
