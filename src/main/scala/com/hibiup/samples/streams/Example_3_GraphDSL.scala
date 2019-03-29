package com.hibiup.samples.streams.sample_2

import akka.actor.ActorSystem
import akka.{Done, NotUsed}
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}

/**
  * 每当您想要执行任何类型的扇入（“多输入”）或扇出（“多输出”）操作时，都需要图表。 图形由简单的Flows构成，这些Flow用作图形中的线
  * 性连接以及用作Flow的扇入和扇出点的连接点 。以下支持的扇入、扇出操作：
  *
  * Fan-out:
  *   Broadcast[T] – (1 input, N outputs) given an input element emits to each output
  *   Balance[T] – (1 input, N outputs) given an input element emits to one of its output ports
  *   UnzipWith[In,A,B,...] – (1 input, N outputs) takes a function of 1 input that given a value for each input emits N output elements (where N <= 20)
  *   UnZip[A,B] – (1 input, 2 outputs) splits a stream of (A,B) tuples into two streams, one of type A and one of type B
  *
  * Fan-in:
  *   Merge[In] – (N inputs , 1 output) picks randomly from inputs pushing them one by one to its output
  *   MergePreferred[In] – like Merge but if elements are available on preferred port, it picks from it, otherwise randomly from others
  *   MergePrioritized[In] – like Merge but if elements are available on all input ports, it picks from them randomly based on their priority
  *   MergeLatest[In] – (N inputs, 1 output) emits List[In], when i-th input stream emits element, then i-th element in emitted list is updated
  *   ZipWith[A,B,...,Out] – (N inputs, 1 output) which takes a function of N inputs that given a value for each input emits 1 output element
  *   Zip[A,B] – (2 inputs, 1 output) is a ZipWith specialised to zipping input streams of A and B into a (A,B) tuple stream
  *   Concat[A] – (2 inputs, 1 output) concatenates two streams (first consume one, then the second one)
  * */

import scala.concurrent.duration._

object Example_3_GraphDSL {
    /** 1) 定义 Source */
    val in = Source[Int](11 to 21)

    /** 2）定义 4 个 Flow */
    val f1, f4 = Flow[Int]           // 头尾两个 Flow 啥都不干
    val f2 = Flow[Int].map(_ + 10)   //  Flow 2 给数字 +10，因此期待得到 21~31
    val f3 = Flow[Int].map(_ - 10)   //  Flow 3 给数字 -10，因此期待得到  1~11

    /** 3）定义 Sink */
    val out = Sink.foreach(println)

    /**
      * 4) 定义一个 Graph。参数是一个隐式传入的 graph builder, builder 是 mutable 的，但是最终生成的 graphDSL 是 immutable
      *    并且线程安全的。如果不关心 Flow 内部的执行过程，也不等待执行结果，那么使用 NotUsed 作为类型参数。
      *
      *    但是如果我们确实希等待结果：
      *      1. 将 Sink 定义在 GraphDSL 的外面（第3步，Source 和 Flow 不是必须的），将外部 Sink 作为参数传递给 create, 它会被
      *      传递给 builder
      *      2. 将 builder 的类型参数设置为 Future[Done]。
      * */
    val g = RunnableGraph.fromGraph(GraphDSL.create(out) { implicit builder: GraphDSL.Builder[Future[Done]] => out =>
        /** GraphDSL.Implicits._ 引入了 ~> 和 <~ 操作符 ( 读作 “edge”, “via” or “to”)  */
        import GraphDSL.Implicits._

        /** 5) 定义扇出点，参数指定要分出几个分支 */
        val broadcast = builder.add(Broadcast[Int](2))

        /** 6）定义扇入点 ，参数指定要接入几个分支 */
        val merge = builder.add(Merge[Int](2))

        /** 7）组合起来 (参考：simple-graph-example.png) */
        in ~> f1 ~> broadcast ~>      // f1 Flow 负责接收 Source，然后交给 broadcast 扇出点
                f2 ~> merge ~>        // broadcast 将数据扇出到 f2，f2 又将数据交给 merge 扇入点
                        f4 ~> out     // f3 汇集了来自扇入点的数据，最后交给 Sink out
        broadcast ~> f3 ~> merge      // 于此同时：broadcast 还将数据扇出到了 f4. f4又将数据扇入到了 merge
        // 因为以上步骤都是 lazy 的，所以可以分别定义，直到 graph.run() 被触发时才开始执行。

        /** 8）关闭 */
        ClosedShape
    })

    def apply(): Unit = {
        /** 1) 同样建立 akka 系统*/
        implicit val system = ActorSystem("reactive-tweets")

        /** 2）获得 GraphDSL 的“物化”器 */
        implicit val materializer = ActorMaterializer()

        /** 3）因为 graph 具有 Future[Done] 返回值（第4步），因此我们可以等待它的执行结果。*/
        Await.result(g.run(), 10 second)
    }
}
