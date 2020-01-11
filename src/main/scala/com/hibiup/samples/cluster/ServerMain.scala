package com.hibiup.samples.cluster

import cats.effect.{ExitCode, IO, IOApp, Resource}
import akka.actor.ActorSystem
import akka.stream.Materializer
import monix.eval._
import cats.implicits._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler.Implicits.global

object ServerMain extends IOApp with StrictLogging{

    import com.hibiup.samples.cluster.routes.Routes

    override def run(args: List[String]): IO[ExitCode] = {
        /**
         * 1) 准备若干个节点（分别赋予不同的服务端口），送往在不同的协程。
         */
        val servers: Seq[Task[Fiber[ExitCode]]] = List((2551, 8080), (2552,8082), (2553, 8083)).map(port => {
            // .start 获得 Task[Fiber]，通过显式调用 runXXX 函数来执行每一个 Fiber
            (Task.shift *> entry(port._1, port._2)).start
        })

        /**
         * 2) 同时启动两个协程，并阻止主线程退出
         */
        IO(Task.sequence(servers).runToFuture.cancel()) *> IO.never.map(_ => ExitCode.Success)
    }

    def entry(akkaPort:Int, httpPort:Int): Task[ExitCode] = {
        val resources = for {
            /**
             * 1）根据传入的端口动态配置节点
             */
            system <- Resource.make{
                Task(ActorSystem(
                    // 名称必须和 Cluster name 一致，参见 application.conf
                    "ClusterSystem",
                    // 动态反映端口号
                    ConfigFactory.parseString(
                        s"""
                           | akka.remote.artery.canonical.port=$akkaPort
                           |""".stripMargin).withFallback(ConfigFactory.load("application-cluster")))
                )
            } { s => Task(s.terminate()) }

            /**
             * 2）用这个 actor system 启动 http 服务，并同时注册前后端 Actor。
             *
             * 2-1）我们将用该 system 启动 http （同时它会注册前端 actor）和后端 AccountServiceActor ，当请求到达 http 时，
             * http 会通过本地 system 来获取它的前端 actor，然后通过前端 Actor 向后端 Actor（AccountServiceActor）发送服务请求。
             * （参考 Routes 的 GET `/local`` 方法）。
             *
             * 2-2）在分布式集群中我们还可以通过 gRPC 协议直接向后端 actor 发送请求，参考 Routes 的 GET `/sharding` 方法和 `/grpc`
             * 方法如何直接获得远程的 actor。
             */
            mat <- Resource.make {
                Task(Materializer(system))
            } { m => Task(m.shutdown()) }
        } yield {
            (system, mat)
        }

        /**
         * 2）注册后端 Actor 并启动前端 Http
         */
        resources
          .use(akka => {
              /**
               * 先注册后端 Actor
               */
              registerBackendService(akka._1, akka._2, "AccountService")

              /**
               * 启动 Http
               *
               * Task.never 阻止线程任务结束（任务结束后 Resource 会回收资源）
               */
              startHttpServer(httpPort, akka) *> Task.never
          })
          .guarantee(Task(println("Application stopped"))).as(ExitCode.Success)
    }

    /**
     * 注册后端 Actor
     */
    def registerBackendService(system:ActorSystem, mat:Materializer, name:String) = {
        import com.hibiup.samples.cluster.services.AccountServiceActor
        AccountServiceActor(system, mat, name)
    }

    /**
     * 启动 Http 服务（并注册前端 Actor）
     */
    def startHttpServer(httpPort: Int, akka: (ActorSystem, Materializer)):Task[_] = {
        import com.hibiup.samples.cluster.routes.Implicits._

        val routes: Routes[Task] = implicitly[Routes[Task]]
        routes.run("localhost", httpPort)(akka)
    }
}
