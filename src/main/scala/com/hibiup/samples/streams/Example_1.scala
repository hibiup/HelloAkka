package com.hibiup.samples.streams

import scala.concurrent.duration.Duration

/**
  *  Akka之Flow相关API总结: https://www.jianshu.com/p/91bf067f1a2a
  * */

object Example_1 {
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

        system.terminate()
        Await.result(system.whenTerminated, Duration.Inf)
    }
}

object what_is_NotUsed extends App{
    def apply() = {
        /**
          * https://manuel.bernhardt.io/2017/05/22/akka-streams-notused/
          *
          * Akka Stream 是一个基于JVM的，非阻塞的，异步序列化串行，并支持背压反应式流之上一个强大的实现。这篇文章并不打算
          * 解释这句话是什么意思，它不是关于Akka Stream怎么工作的。这篇文章的目的只是解释 Akka Stream 类型中 NotUsed 类
          * 型签名到底是什么。顺带解释一下Akka Stream的基本设计思想。
          *
          * 为了理解 NotUsed 我们需要先明白 Akka Stream 是一个混合了两种“值”概念的怪兽：一个是跑在流里的“值”；和一个
          * 最终产出，我们能够看到的“值”。看，Akka Stream 是运行在封闭状态下的，也就是说它有一个起点和一个终点（或
          * 者说一个源（Source）和一个池（Sink）），并且从外部无法窥探它的内部。这并没什么问题，如果你只是想吧数据从A传递
          * 到B点，但是如果你想让Stream给你看看它都产生了什么值，那么你将做不到，因为你接触不到内部。
          *
          * 让我们看一下下面这个例子（感谢我无与伦比的绘画技巧）：![]src/main/resources/stream.png
          */
        import akka.stream.scaladsl._
        import scala.concurrent._

        import akka.actor.ActorSystem
        import akka.stream.ActorMaterializer

        implicit val system = ActorSystem("testSystem")
        implicit val materializer = ActorMaterializer()

        val source = Source(List("a", "b", "c"))
        val sink = Sink.fold[String, String]("")(_ + _)

        val runnable: RunnableGraph[Future[String]] = source.toMat(sink)(Keep.right)
        val result: Future[String] = runnable.run()

        /**
          * 到目前为止，除非你已经了解了 Akka Stream，否则我非常确定你已经把我跟丢了，这却是也是我想要的，这样你才能了解
          * NotUsed 是什么。现在我们有一个 Source，它连续发射出 "a", "b" 然后是 "c"。接下去我们有一个 Sink 来接受并合并
          * 它们。我们把这两个东西何在一起赋给 RunnableGraph － 之所以取这样一个名字是因为它有起点和终点，这个“图”
          * （Graph）是封闭的，并且因此我们可以运行它。其中让我们很困惑的是下面这一行：
          *
          *   source.toMat(sink)(Keep.right)
          *
          * 接上话题：我们不仅希望我们的信息从Source流到Sink，我们也希望充Sink处获得最终的结果（"a","b"和 "c"的串联）。
          * 这在 Akka 术语里这叫“物化”(materialization)，而为了执行物化，我们需要一个“物化器”。所以 source.toMat(sink)
          * 的意思是“将Source连接到Sink并产生一个普通人可以从外部访问的值”。“好的，非常好！”你会说，“但是 Keep.right
          * 是个什么？”。你这样问是正确(right)的（双关语），因为答案并不明显。
          *
          * 给你一点提示：特别是如果你是一个母语是从右到左语言的人：在Akka Streams中，图形从左向右流动。
          *
          * Akka Streams允许或者从左或者从右开始执行物化（或者叫“keep”）。在这里，我们希望从右边开始（流入Sink），这就
          * 是为什么我们指定 Keep.right。你也可以要求从左边开始（在这个例子中这没什么意义，但是在其他更复杂的情况下，它完
          * 全合理）。实际上，现在开始你应该开始读一下这一篇Akka文档：
          * https://doc.akka.io/docs/akka/2.5/stream/stream-composition.html?language=scala#Materialized_values
          *
          * 在我们的例子中，物化值将会是一个String,但是如果我们并不在乎这个物化值呢？如果我们只是希望流能够执行，将元素从
          * 一个地方推向另一个地方呢？是的，你猜对了，这就是我们用 NotUsed 来表达“我不在乎这个物化值是什么。”
          **/
        import scala.concurrent.duration.Duration._
        Await.result(result, Inf).foreach(println)

        system.terminate()
        Await.result(system.whenTerminated, Duration.Inf)
    }

    apply()
}