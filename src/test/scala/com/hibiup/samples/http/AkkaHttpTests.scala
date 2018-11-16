package com.hibiup.samples.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, _}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import org.scalatest.FlatSpec
import spray.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class AkkaHttpTests extends FlatSpec  {
    /** 1) 定义测试类， 通过上下文隐式或 akka.testkit.TestKit（二选一）　获得 ActorSystem，可以重用启动服务的时候建
      *    立的那个实例（也可以新建一个system）。同时要求一个函数参数 f 用于结果回调处理。*/
    object AkkaHttpTest /*extends TestKit(system)*/ {
        def apply(uri:Uri,
                  method: HttpMethod,
                  entity:RequestEntity=HttpEntity.Empty)(f:HttpResponse => Future[Either[StatusCode,JsObject]])
                 (implicit as: ActorSystem, ec:ExecutionContext) =
            /** 2) 从 actorSystem 中获得 Http() 客户端*/
            Http()
                    /** 3) 得到 Future HttpRequest 用于发起请求 */
                    .singleRequest(HttpRequest(method, uri, Nil, entity))
                    /** 4) 将结果映射到回调函数 f（返回 Future）。这个 Future 将返回 Unmarshaller 解析后的结果。*/
                    .map(f)
    }

    /** 5) 定义一个 Unmarshaller 用于解析返回的结果。*/
    import spray.json._
    implicit val um:Unmarshaller[HttpEntity, JsObject] = {
        Unmarshaller.byteStringUnmarshaller.mapWithCharset { (data, charset) =>
            data.decodeString(charset.value).parseJson.asJsObject
        }
    }

    val port = 8082

    "Get method" should "works with Akka http route" in {
        /* 启动服务 */
        val route = FirstRoute(port)

        /** 1) 获得测试类 AkkaHttpTest 中的 Http() 所需要得 ActorSystem 和 ActorMaterializer  */
        implicit val system = ActorSystem("my-system")
        implicit val actorMaterializer = ActorMaterializer()
        implicit val dispatcher = system.dispatcher

        /* 用 AkkaHttpTest 测试类来请求服务，我们将在这个类中（从 Akka http 中）创建一个 http 客户端来请求服务。 */
        val res = Await.result(AkkaHttpTest(Uri(s"http://localhost:${port}/stop"), HttpMethods.GET){
            /** 2) 定义 AkkaHttpTest.apply 的回调函数参数 f。需要 Unmarshaller 作为 Unmarshal.to 的隐式参数（参考第 5 步） */
            response => Unmarshal(response.entity).to[JsObject].map(Right(_))
        }, 10 seconds)

        /* 评估结果。*/
        res.map{x =>
            println(x)
            assert(x.getOrElse("Result") eq "Stop")
        }
    }

    "Post method" should "create an order record" in {
        import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
        import com.hibiup.samples.http.FirstRoute.{Item, Order}
        import spray.json.DefaultJsonProtocol._

        implicit val system = ActorSystem("my-system")
        implicit val actorMaterializer = ActorMaterializer()
        implicit val dispatcher = system.dispatcher

        implicit val itemFormat = jsonFormat2(Item)    // Item 有两个类型参数: String, Long
        implicit val orderFormat = jsonFormat1(Order)  // Order 有一个类型参数：Item

        /* 启动服务 */
        val route = FirstRoute(port)
        val itemNumner = 1
        val itemValue = "Something"

        // First get supposed to get 404
        Await.result(AkkaHttpTest(Uri(s"http://localhost:${port}/item/${itemNumner}"), HttpMethods.GET){
            response =>  response.status match {
                case StatusCodes.OK => Unmarshal(response.entity).to[JsObject].map(Right(_))
                case x => Future.successful(Left(x))
            }
        }, 10 seconds).map{x =>
            assert(x.getOrElse() == StatusCodes.NotFound)
        }

        Marshal(Order(Item(itemValue, itemNumner)::Nil)).to[RequestEntity] flatMap { e =>
            Await.result(AkkaHttpTest(Uri(s"http://localhost:${port}/order"), HttpMethods.POST, e) {
                case response@HttpResponse(StatusCodes.OK, _, _, _) => Unmarshal(response.entity).to[JsObject].map(Right(_))
                case x => Future.successful(Left(x.status))
            }, 10 seconds)
                    .map{x =>
                        println(x)
                        assert(x.getOrElse("Result") eq "order created")
                    }
        }

        // Another get supposed to be able to get the order back
        Await.result(AkkaHttpTest(Uri(s"http://localhost:${port}/item/${itemNumner}"), HttpMethods.GET){
            case response@HttpResponse(StatusCodes.OK, _, _, _) => Unmarshal(response.entity).to[JsObject].map(Right(_))
            case x => Future.successful(Left(x.status))
        }, 10 seconds).map{x =>
            println(x)
            assert(x.getOrElse("name") eq itemValue)
        }
    }
}
