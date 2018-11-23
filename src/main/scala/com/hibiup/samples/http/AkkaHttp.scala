package com.hibiup.samples.http

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._

import scala.concurrent.{Await, Future, Promise}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.util.ByteString
import spray.json.DefaultJsonProtocol._

import scala.concurrent.ExecutionContext.Implicits.global

object FirstRoute {
    // domain model
    final case class Item(name: String, id: Long)
    final case class Order(items: List[Item])

    // jsonFormat 是 Marshaller, 后面的数字代表类型参数的个数，最多到 22
    implicit val itemFormat = jsonFormat2(Item)    // Item 有两个类型参数: String, Long
    implicit val orderFormat = jsonFormat1(Order)  // Order 有一个类型参数：Item
    //implicit val s = jsonFormat1(StatusCodes.NotFound(""))

    // mockup 数据集
    var orders: List[Item] = Nil  // 数据集
    def fetchItem(itemId: Long): Future[Option[Item]] = Future {
        orders.find(o => o.id == itemId)
    }
    def saveOrder(order: Order): Future[Done] = {
        orders = order match {
            case Order(items) => items ::: orders
            case _            => orders
        }
        Future { Done }
    }

    def apply(port:Int) {
        import scala.concurrent.duration._

        //val ending_promise = Promise[Boolean]()

        /** 1) 新建 Akka system */
        implicit val system = ActorSystem("my-system")
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        /** 2) 定义 route */
        val route =
        /* get 是一个 Directive0 实例（val），定义如下：
             type Directive0 = Directive[Unit]
           Directive[L] 含有以下方法：
             def apply(f: L ⇒ Route): Route
           它接受一个参数，返回下一级 Route */
            get {
                /* 1) pathPrefix 返回一个 FutureDirectives. 它接受一个 Matcher[A] 类型的参数，LongNumber 表示匹配一个 Long
                      类型的数字*/
                pathPrefix("item" / LongNumber) { id =>
                    var orders: List[Item] = Nil
                    val maybeItem: Future[Option[Item]] = fetchItem(id)

                    /* 2) 在 FutureDirectives 的 onSuccess 中预定义 Future 成功取得结果后的处理。 */
                    onSuccess(maybeItem) {
                        /* 3) 成功后，complete 将 item 传给（ 隐式参数） ToResponseMarshallable.apply() 来将结果它转换成
                              JSON。ToResponseMarshallable.apply() 接受一个 implicit 参数 ToResponseMarshallable，
                         *    ToResponseMarshallable 由 akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._ 包
                         *    引入。而 ToResponseMarshallable 本身又接受一个 ToResponseMarshaller[A] 隐式参数来转化 Item，
                         *    而这个 ToResponseMarshaller 在这个上下文中就是 itemFormat */
                        case Some(item) => complete(item)
                        case None       => complete(StatusCodes.NotFound)
                    }
                }
            } ~   /** 2-1） “~” 连接两个 route */
            post {
                /* 1) 和 pathPrefix 一样，path 返回一个 FutureDirectives */
                path("order") {
                    /* 2）entity 接受 post 传入的参数。通过 as 的隐式 orderFormat 参数作为逆化器将接收到的数据
                     *    还原成需要的类型。（同时需要　import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._） */
                    entity(as[Order]) { order =>
                        val saved: Future[Done] = saveOrder(order)
                        /* 3）FutureDirectives 的 onComplete 中预订一成功后的处理（返回成功信息给客户端）。*/
                        onComplete(saved) { done =>
                            complete("""{ "Result" : "order created" }""")
                        }
                    }
                }
            } ~
            get {
                pathPrefix("order" / Segment) { email =>
                    complete {
                        email match {
                            case "a" => {
                                HttpResponse(StatusCodes.OK,
                                    entity = HttpEntity(ContentTypes.`application/json`, ByteString(s"""{"existing": true}""")))
                            }
                            case "b" => {
                                HttpResponse(StatusCodes.NotFound,
                                    entity = HttpEntity(ContentTypes.`application/json`, ByteString(s"""{"exception": "${new RuntimeException}"}""")))
                            }
                        }
                    }
                }
            }

        /** 3）绑定到端口 */
        val bindingFuture = Http().bindAndHandle(route, "localhost", port)
    }
}
