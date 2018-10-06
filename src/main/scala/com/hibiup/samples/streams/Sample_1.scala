package com.hibiup.samples.streams

package Sample_1 {


    object HelloAkkaStream {
        import akka.stream._
        import akka.stream.scaladsl._

        import akka.{ NotUsed, Done }
        import akka.actor.ActorSystem
        import scala.concurrent._

        import java.nio.file.Paths
        import akka.util.ByteString
        import scala.concurrent.duration._

        def apply() = {
            /** 1）初始化一个 Akka System */
            implicit val system = ActorSystem("testSystem") // 隐式生成 ActorRefFactory 的子类 ActorSystem。

            /** 2）基于 ActorSystem 生成 Akka Stream 的执行引擎。
              *
              * 这个引擎将从数据源中读取数据，并将数据传递给执行函数。 */
            implicit val materializer = ActorMaterializer() // 隐式获得 system。并生成隐式 ActorMeterializer 实例。

            implicit val ec = system.dispatcher    // 隐式生成 ExecutionContext 的子类 ExecutionContextExecutor

            /** 3）定义一个数据发射源 Source。
              *
              * 它有两个参数，第一个是数据类型，比如以下这个数据源将产生 1 到 100 整数。
              * 第二个参数任意，可以用于产生一些辅助值，比如可以是数据源的IP地址等相关数据。如果没有可以用 NotUsed */
            val source: Source[Int, NotUsed] = Source(1 to 100)

            /** 4）从数据源中萃取数据并处理。
              *
              * 启动数据源工作流的方法都以 run 开头，该方法隐式从上下文中获得 akka stream 的执行引擎。最终返回一个
              * Future。在本例中执行引擎将持续从 source 中读取数据然后传递给第一个参数，第一个参数是将值打印出来。*/
            val done: Future[Done] = source.runForeach(println) // 隐式获得 materializer 参数

            /** 4-1) source 是可以重用的，再定义一个将数据写入文件的处理流程。
              *
              * scan 方法类似 fold，初始值是 BitInt(1),然后逐个取出 Source 中的元素 next，得到每一级的阶乘值 acc，存入新的 Source。*/
            val factorials = source.scan(BigInt(1))((acc, next) ⇒ {/* println(acc, next);*/ acc * next })
            val accent: Future[IOResult] =
                factorials.map(num ⇒ ByteString(s"$num\r\n"))
                    .runWith(FileIO.toPath(Paths.get("C:\\\\Temp\\numbers.txt")))

            /** 5）等待 Stream 处理结束后关闭 Akka System。
              *
              * 关闭方法由 Future 的 onComplete 回调函数执行，回调函数隐式获得 AkkaSystem 的执行上下文。*/
            val result = Future.sequence(List(done,accent))
            result.onComplete(_ ⇒ { println("Finish!!");system.terminate() }) // 隐式获得 ec

            /** 6) 因为 onComplete 是异步操作，如果是最后一行，任务可能会被迫终止，因此退出前必须等待 result 完成。*/
            Await.result(result, 10 seconds)
        }
    }
}
