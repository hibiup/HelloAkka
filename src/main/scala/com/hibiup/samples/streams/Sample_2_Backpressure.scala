package com.hibiup.samples.streams.sample_2

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}

object Sample_2_Backpressure {
    /** 下面这个例子通过模拟作者 Author 在 Tweet 发布标签 Hashtag(#abc) 来演示如何解决背压问题（backpressure） */

    // Model
    // 作者
    final case class Author(handle: String)
    // 标签
    final case class Hashtag(name: String)
    // Tweet消息 接受作者、时戳、和推文三个参数。
    final case class Tweet(author: Author, timestamp: Long, body: String) {
        // hashtags 分解推文，找出其中的标签(#开头的单词)，转换成 Set
        def hashtags: Set[Hashtag] = body.split(" ").collect {
            case t if t.startsWith("#") ⇒ Hashtag(t.replaceAll("[^#\\w]", ""))
        }.toSet
    }
    // Model


    def apply(): Unit = {
        import scala.concurrent.duration._

        /** 1) 同样建立 akka 系统*/
        implicit val system = ActorSystem("reactive-tweets")
        implicit val materializer = ActorMaterializer()

        /** 1）定义 Source：tweet 消息集合 */
        val tweets: Source[Tweet, NotUsed] = Source(
            Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
                    Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
                    Tweet(Author("bantonsson"), System.currentTimeMillis, "aa #bantonsson #rocks !") ::
                    Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #mmartynas # !") ::
                    Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #mmartynas # !") ::
                    Nil)

        /** 2）定义 Flow */
        val tagFlow = Flow[Tweet].map(_.hashtags)   // 逐条消息过滤出其中的每个标签。
                .reduce(_ ++ _)                     // 合并成一个Set并去除重复的标签。
                .mapConcat(identity)                // 通过与“幺元”的结合还原Stream中的元素类型（否则下面的 "_" 将无法识别类型 ）
                .map(_.name.toUpperCase)            // 取出 tag 转成大写
                /** 为 Flow 制定一个 Buffer, 长度为2，并指定 Overflow 策略：
                  * dropTail:      丢弃后进入的数据
                  * backpressure:  当buffer满了后进行背压，在此情况下上游将停止发送直道缓冲器有新的空间可用。 */
                .buffer(2, OverflowStrategy.dropTail)

        /* mapConcat 相当于 flatMap，它会提取出容器中的元素重新组合成 flatten set */
        //val flatTagFlow: Source[Hashtag, NotUsed] = tweets.mapConcat(_.hashtags)

        /** 3） 定义一个 Sink 处理 HashTag */
        val writeHashtags: Sink[String, Future[Done]] =  Sink.foreach{x =>
            Thread.sleep(1000)    // 每 1 秒处理一条数据
            println(x)
        }

        /** 4) 串起 Source -> Flow -> Sink */
        val result = tweets.via(tagFlow)
                .throttle(1, 10 microsecond)  // throttle 可以将流速限制在每10毫秒一条。
                /** 在另外一个线程里异步执行 Sink 任务。因为消息发送的速度（10mm）快于处理速度（1m），因为Buffer只能存放 2 条，
                  * 并且采用了 dropTail 策略，因此最终也就只接收了两条消息 */
                .async.runWith(writeHashtags)

        // 等待结束
        Await.result(result, 10 seconds)
    }
}
