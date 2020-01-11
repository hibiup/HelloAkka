package com.hibiup.samples.streams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Attributes, Graph, Outlet, SourceShape}
import akka.stream.scaladsl.{Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

package Example_6_GraphStage {

import akka.stream.stage.InHandler
import akka.stream.{Inlet, Materializer, SinkShape}

/**
      * GraphStage 抽象是GraphDSL.create（）方法的对应物。 GraphStage 的不同之处在于它创建的操作符不能被分割成更小的操作符，
      * 并它允许以安全的方式维护其中的状态。
      * */
    object GraphStageExample extends App {
        /**********************************************
          * 我们将构建一个新的 Source(GraphStage)，它将从 1 发出数字直到它被取消。
          *
          * 1）继承 GraphStage 接口。参数是 SourceShape[输出类型]。
          * */
        class NumbersSource extends GraphStage[SourceShape[Int]] {
            /**
              * 定义一个作为 Port 的唯一名称，并给予输出类型作为参数
              * */
            val out: Outlet[Int] = Outlet("NumbersSource")

            /**
              * 然后根据 port 得到 SourceShape[输出类型]。在 Akka stream 中，某个“操作（Operator）接口” 被称为 “Shape”。
              */
            override val shape: SourceShape[Int] = SourceShape(out)

            /**
              * createLogic 是 GraphStage 包含业务逻辑的方法. 也就是说我们要在这个方法中实现数字的发送.
              */
            override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
                /**
                  * 为了从背压流中的源发射，首先需要接受来自下游的需求。要接收需求事件，需要向输出端口（Outlet）注册 OutHandler 的子
                  * 类。此处理程序将接收与端口生命周期相关的事件。在我们的例子中，我们需要覆盖 onPull()，实现自由地发射出单个元素。
                  *
                  * 还有另一个回调，onDownstreamFinish()，如果下游要取消服务则调用它。由于该回调的默认行为是停止操作，因此我们不需
                  * 要覆盖它。
                  * */
                private var counter = 1
                setHandler(out, new OutHandler {
                    override def onPull(): Unit = {
                        push(out, counter)
                        counter += 1
                    }
                })
            }
        }

        /**
          * 2) 为了将此Graph转换为真正的 Source，我们需要使用　Source.fromGraph 方法将其包装起来．
          *
          * A GraphStage is a proper Graph, just like what GraphDSL.create would return
          * */
        val sourceGraph: Graph[SourceShape[Int], NotUsed] = new NumbersSource
        val numbersSource: Source[Int, NotUsed] = Source.fromGraph(sourceGraph)

        /**
          * 3) 物化 Source
          * */
        import scala.concurrent.ExecutionContext.Implicits.global

        implicit val sys = ActorSystem("demo-system")
        implicit val mat = Materializer(sys)
        val result1: Future[Int] = numbersSource.take(10).runFold(0)(_ + _)  // 1+2+3...+10 = 55
        result1.onComplete(_ => sys.terminate())

        println(Await.result(result1, Duration.Inf))   // 55


        /**********************************************
          * 同样也可以构建一个 Sink
          */
        class StdoutSink extends GraphStage[SinkShape[Int]] {
            /**
              * 不同的是 Sink 使用 Inlet 而不是 Outlet 来注册名字.
              * */
            val in: Inlet[Int] = Inlet("StdoutSink")
            override val shape: SinkShape[Int] = SinkShape(in)

            override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
                /**
                  * 大多数 Sink 在创建后需要立即请求上游数据, 这可以通过调用preStart()回调中的pull(inlet)来完成。
                  * */
                override def preStart(): Unit = pull(in)
                /**
                  * InHandler.onPush 用来处理接收到的事件.
                  * */
                setHandler(in, new InHandler {
                    override def onPush(): Unit = {
                        println(grab(in))
                        pull(in)
                    }
                })
            }
        }
    }
}
