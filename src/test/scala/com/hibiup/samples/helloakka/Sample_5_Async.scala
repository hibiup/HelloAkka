package com.hibiup.samples.helloakka

import org.scalatest.FlatSpec
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.hibiup.samples.helloakka.Example_5_Couroutine._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ForkJoinPool


class Example_5_Couroutine extends FlatSpec{
    /**
      * 们都知道在 RX 编程中，消息驱动扮演了重要的角色，它是任务驱动的重要手段之一。于是很多程序员就因此误认为掌握了消息驱动就等于掌握
      * 了异步编程，这实际上是对异步编程的一大误解。消息驱动本质上只是任务的驱动手段之一，它身并不带来真正的异步，异步的关键并不在你以
      * 什么方式去调用另一个任务，更重要的是你如何管理异步任务的生命周期。如果我们不能了解这一点，那么消息驱动将仅仅停留在表面，只是作
      * 比为传统函数调用更酷的的替代品，并不能构建出正真的异步程序。为此我以 akka 为平台来大致讲解一下什么才真正的异步驱动。
      *
      * 首先我们来看一下以下这段代码：
      */

    "Greeting akka actor" should "be driven by message" in {
        implicit val timeout = Timeout(10 seconds)

        val greetingActorRef = ActorSystem("testSystem").actorOf(Props[GreetingActor])
        val ret = Await.result(greetingActorRef ? Who("World"), timeout.duration)
        ret match {case Greeting(msg:String) => println(msg); assert(msg == "Hello, World!")}
    }

    /** *
      * 以上这段代码我们用 akka 系统实现了消息驱动，通过一个 actor 得到问候语。先不纠结于此架构对业务来说有多大的价值，让我们仅仅纯粹
      * 从架构的角度来思考一下它的价值有多大？很遗憾，答案是这个实现毫无价值，连“几乎”都配不上。(akka 的分布式附加值不是我们本次考察
      * 的重点）。为什么呢？为了简化问题，我们现在抛开 akka，仅以纯粹的多线程来实现这个例子中的任务模型。
      */
    it should "be able to be replaced with pure Future model" in {
        import scala.concurrent.ExecutionContext.Implicits.global
        implicit val timeout = Timeout(10 seconds)

        val who = "World"
        val greetingFuture = Future(s"Hello, ${who}!")
        val greeting = Await.result(greetingFuture, timeout.duration)
        println(greeting)
        assert(greeting == "Hello, World!")
    }

    /**
      * 在上面这个例子中我们用了一个 Future 模型来取代 akka，我们可以很清楚地看到两者的的线程调度模式其实是一样的。都以主线程启动了一
      * 个任务线程来生成问候语，然后主线程等待任务线程执行结束后主线程打印出问候语。在这个例子中我们甚至连消息驱动都没有使用就实现了同样
      * 的线程调度。也就是说消息驱动仅仅就线程模型来说是可有可无的。所以消息驱动并不是异步编程的充分条件。甚至，如果你更细心一些，你还会
      * 产生疑问，以上两个例子根本不能称为异步架构，最多只是一个普通的多线程架构。更甚至，这个多线程架构相对于传统单线程架构来说，也毫无
      * 优势。我们完全可以将任务线程和主线程合并，只用一个主线程既生成问候语，又打印问候语，在架构的价值上也是完全等价的，因为在工作线程
      * 执行的过程中，主线程是空闲的，在这期间它完全可以不需要工作线程（如果不考虑分布的话）取而代之。难道不是么？
      *
      * 为了进一步说明这个问题，我们以打乒乓球为例。在乒乓球比赛中，选手A和选手B相当于两个线程，互相击球就相当于互相调用，当选手A将球击
      * 打给B后，任务就交给了选手B，在B回球的过程中，选手A其实处于等待状态。这在现实比赛中习以为常，但是在电脑的CPU中，这是极大的资源浪
      * 费，如果选手A的速度足够快，他完全可以回家吃个饭再回来，没有必要专门等待对手的回球。如果他够敬业的话，他甚至他可以跑到球桌的对面
      * 去回击自己发出的球，那么他一个人（线程）就可以完成这场比赛，根本不需要选手B。这也就是我们上面两个例子阐述的问题。所以以上都不是
      * 真正的异步架构。为此我们有必要来澄清一下有关异步的几个误区：
      *
      * 误区一：消息驱动是异步编程重要的任务驱动手段，但不是异步架构的充分条件。
      * 误区二：真正的异步架构其实并不刻意追求多线程，它追求的是最有效的CPU利用率。
      *
      * 那么怎样才是最有效的CPU利用率呢？让我们还是以乒乓球比赛为例。但是我们现在要改一下规则，现在我们增加一个称为“选手池”的东西，它用
      * 来存放没有上场的选手，其次、我们也不再不区分你我阵营，所有的选手都来自这个“选手池”，还有，我们也不再规定开球的选手只能在他的发球
      * 侧打比赛，甚至不要求他在开出球之后必须继续在球桌边等待对方的回球。比赛的评判标准仅仅以乒乓球能够在球桌两边被不断被击打为唯一评判
      * 标准。也就是说，我们关注的是业务能够被持续执行下去，而不关注由多少线程，以什么样的方式，在何时何地执行下去。那么我们将可能会看到
      * 这样一种比赛场景，选手A发出球后就离开了比赛现场回到“选手池”等待下一个调配去了，而选手B在回球后也离开了现场打电话去了，当球被打回
      * 到发球侧的时候，选手C恰逢其时地出现了，他替已经下场的选手A将球击回了另一边，而球在另一边即将落地前，选手A及时回到了球桌的另一边
      * 又将球击了回来……。
      *
      * 甚至如果他们三人的速度足够块的话，就能够以同样的方式同时进行多场比赛！是的，我们以某种方式将原本两人玩一场比赛的模式扩展成了三人
      * 同时玩多场比赛。而且，更重要的是，不管比赛有多激烈，任何一场比赛中同时只有一个选手在场！是的！虽然在场的可能不是同一个选手，但是
      * 当一个选手在击球的时候，另外两个选手不是在休息区就是在另外一个比赛场上！他们绝不会同时出现在同一个场地中去等待任何其他人击球！也
      * 就是说，虽然这是个多线程的程序，但是每个业务主线都相当于单线程！但是他们能够服务超过总线程数的任务！因为我们充分地利用了选手的等
      * 待时间，这样就极大地利用了有限的选手，完成了更多的击球动作，整体比赛效率因此得到提升。
      *
      * 那么现在让我们以程序的方式来实现这个新的调度模式：
      **/

    "A real async structure" should "has higher productive effectiveness" in {
        implicit val timeout = Timeout(10 seconds)

        /** 新建一个线程，其中有 3 个 player */
        implicit val player: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(3))
        implicit val system = ActorSystem.create("testSystem", null, null, player)
        val gameRef = system.actorOf(Props[GameActor])

        /** 定义击球动作（业务方法） */
        def playGame[T](actor:ActorRef, hit:T):Unit = (actor ? hit).map{ case x:Hit => playGame(actor, x) }

        /** 同时开出 100 个球（对应 100 场比赛） */
        for (i <- 1 to 100) yield playGame(gameRef, Hit(i,1))

        /** 让游戏持续 1 秒 */
        Thread.sleep(1000)

        /**
          * 我的电脑执行后大致得到这样一个结果：
          *
          * Thread-23: Ball 1 with 1 hits!
          * Thread-22: Ball 2 with 1 hits!
          * Thread-22: Ball 3 with 1 hits!
          * Thread-22: Ball 1 with 2 hits!
          * Thread-22: Ball 4 with 1 hits!
          * Thread-22: Ball 5 with 1 hits!
          * Thread-22: Ball 2 with 2 hits!
          * Thread-22: Ball 6 with 1 hits!
          * 。。。
          * 。。。
          * Thread-23: Ball 22 with 963 hits!
          * Thread-23: Ball 61 with 962 hits!
          * Thread-23: Ball 65 with 962 hits!
          * Thread-23: Ball 63 with 962 hits!
          * Thread-23: Ball 62 with 962 hits!
          * Thread-23: Ball 3 with 965 hits!
          * Thread-23: Ball 66 with 962 hits!
          * Thread-21: Ball 23 with 963 hits!
          * Thread-21: Ball 68 with 962 hits!
          * Thread-21: Ball 69 with 962 hits!
          * Thread-21: Ball 11 with 964 hits!
          * Thread-21: Ball 64 with 962 hits!
          * Thread-21: Ball 24 with 963 hits!
          *
          *
          * 你可能看到的和我略有不同，但是我们可以很清晰地看到总共只有三个工作线程在工作，并且他们都随机地出现在任何一场比赛中，每个球
          * 的击打总次数甚至高达近 1000 次！也就是说我们只用三个并行线程就完成了 100 个并发任务，并且每个任务被调度了近 1000 次！过
          * 程中没有任何一个线程在执行任何一项任务时等待任何一个其他调度的完成，但是即便如此繁忙，也没有任何一颗球落在地上。在这个例子
          * 中我们对异步多任务系统总结以下几点：
          *
          * 一、拒绝在线程中使用任何等待操作（最后的 sleep 除外。）
          * 二、不必惧怕任何线程调度的结束，并且应该欢迎线程尽早结束。只有线程结束它才会回到线程池已备重新调度，而等待的线程不能被重复
          *    使用。
          * 三、线程并不是越多越好，多余的线程如果没有机会参与任务调度，那么并不能提高整体效率。笔者将线程数设置为与这台试验机内核数相
          *    同的 24 个后再次执行得到的结果甚至略低于 3 线程的结果。
          * 四、akka actor 的个数与线程数，甚至任务数没有关系，在这个例子中，无论多少个任务，多少个线程，都复用了同一个 actor
          * 五、消息驱动与线程调度属于不同的范畴，两者也没有直接关系，消息驱动解决的是任务的解耦和分布能力，同时消息可能带来的负面影响
          *    是通道的宽度，最后笔者附上这个程序的 Future 模型，在这模型中因为没有消息瓶颈，每个线程被调度的次数甚至可以高达 1200
          *    次以上！但是程序也因此失去了分布式部署的能力，但是这属于异步系统另一个话题领域，本文不做详细介绍。
          * */
    }

    it should "similar to pure Future model" in {
        /** 新建一个线程，其中有 3 个 player */
        implicit val player: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(3))

        /** 定义击球动作（业务方法） */
        def playGame(hit:Hit):Future[Hit] = Future[Hit] {
                println(s"Thread-${Thread.currentThread.getId}: Ball ${hit.ball} with ${hit.count} hits!")
                hit
            }.map { case Hit(ball, count) => playGame(Hit(ball, count + 1)) }.asInstanceOf[Future[Hit]]

        /** 同时开出 100 个球（对应 100 场比赛） */
        for (i <- 1 to 100) yield playGame(Hit(i,1))

        /** 让游戏持续 1 秒 */
        Thread.sleep(1000)
    }

    /**
      * 最后，以一个线程或者少数线程服务于多于线程数的任务，并且保持任务不停顿的模型有一个专有称呼，叫做“协程”。协程模型其实就是一种特
      * 定的异步驱动模型。而也因此 AKKA 系统也被称为 SCALA 的协程架构。
      **/
}

// Companion object
object Example_5_Couroutine {
    case class Greeting(msg: String)
    case class Who(name: String)

    class GreetingActor extends Actor {
        override def receive: Receive = {
            case Who(name: String) => sender ! Greeting(s"Hello, ${name}!")
        }
    }

    case class Hit(ball: Int, count: Int)
    class GameActor extends Actor {
        implicit val timeout = Timeout(10 seconds)

        override def receive: Receive = {
            case Hit(ball: Int, count: Int) => {
                println(s"Thread-${Thread.currentThread.getId}: Ball $ball with $count hits!")
                sender ? Hit(ball, count + 1)
            }
        }
    }
}
