package com.hibiup.samples.cluster.routes

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import monix.eval.Task
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.hibiup.samples.cluster.{Foo, Message, Result}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import monix.execution.Scheduler.Implicits.global


trait Routes[F[_]] {
    def run(host:String, port:Int)(resource: (ActorSystem, Materializer)): F[_]
}

object Implicits {
    implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

    implicit object RoutesImpl extends Routes[Task] {
        override def run(host:String, port:Int)(resource: (ActorSystem, Materializer)): Task[_] = Task{
            implicit val (system, mat) = resource
            /**
             * 1) 启动当前节点（Node）并注册前端 Actor，这个 Actor 将负责和后端（AccountServiceActor）之间的通讯.
             *
             * （参见 FrontEndActor 的说明）
             */
            system.actorOf(Props[RouteActor], name="Frontend")

            /**
             * 启动一个Http 服务来测试 Cluster
             */
            val _: Future[Http.ServerBinding] = Http().bindAndHandle(routes(system), host, port)

            system.log.info("Routes are ready!")
        }

        /**
         * Http 服务
         */
        private def routes(system:ActorSystem): Route = {
            get {
                /**
                 * 2-1）在当前的 system 中只能获得本地注册的（Frontend） actor。这种方法无法获取其他节点中的 actor。
                 * 但是本地 Frontend 中注册有其他节点上的 Backend Actor
                 */
                path("local") {
                    val f = system.actorSelection("user/Frontend").resolveOne().flatMap{actorRef => {
                        (actorRef ? Message("An new message!")).mapTo[Result].map(result =>
                            StatusCodes.OK -> s"""{
                                 |  "Message": ${result.msg}
                                 |}""".stripMargin
                        ).recover{case ex =>
                            StatusCodes.InternalServerError -> s"""
                                 |{
                                 |  "Message": ${ex.getMessage}
                                 |}""".stripMargin
                        }}
                    }.recover{case ex =>
                        StatusCodes.InternalServerError -> s"""
                         |{
                         |  "Message": ${ex.getMessage}
                         |}""".stripMargin
                    }.map(resp => complete(resp))

                    Task.fromFuture(f).runSyncUnsafe()
                }
            } ~
            get{
                /**
                 * 2-2）Cluster sharding 或 Cluster grpc 模式可以获得远程其他节点上的 ActorSystem 中注册的 actor：
                 *
                 * Sharding - https://doc.akka.io/docs/akka/2.5/cluster-sharding.html
                 * Cluster client - https://doc.akka.io/docs/akka-grpc/current/index.html
                 */
                path("sharding") {
                    // TODO:
                    complete(
                        s"""{
                           |  "Message": "Hello World!"
                           |}""".stripMargin)
                }
            } ~ path("hello") {
                import com.hibiup.samples.cluster.twirl.TwirlSupport._
                get {
                    complete {
                        /**
                         * sbt-twirl plugin 自动生成 `html` 编译引擎实例 (以下这个名为 html 的对象)，html 实例需要一个
                         * twirlMarshaller[Html](text/html`)隐式来工作，这个隐式定义在 TwirlSupport 中。
                         *
                         * 如果报告找不到 html 引擎，执行 `sbt clean compile` 自动生成它，然后刷新 sbt 配置.
                         *
                         * html 引擎依据 twirl 模版目录下的 xxx.scala.html 文件名动态生成方法名 xxx，例如 html.hello 对应
                         * hello.scala.html 模版。模版名的第三段式 'html' 对应的就是 html 编译引擎。
                         *
                         * sbt-twirl 除了 html 引擎外，还支持 txt, json, xml 等引擎，只需修改模版的第三段式名称并使用动态生成的
                         * 其他同名引擎即可。参考：
                         *
                         * https://github.com/btomala/akka-http-twirl/blob/master/src/test/scala/akkahttptwirl/ExampleApp.scala
                         */
                        html.hello.render(Foo("Hello!"))
                    }
                }
            }
        }
    }
}
