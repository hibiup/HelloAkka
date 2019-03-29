package com.hibiup.examples

import org.scalatest.FlatSpec
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.hibiup.examples.Example_5_Couroutine._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ForkJoinPool


class Example_5_Couroutine extends FlatSpec{
    /**
      * 们都知道在 RX 编程中，消息驱动扮演了重要的角色，它是任务驱动的重要手段之一。于是很多RX新手就因此误认为掌
      * 握了消息驱动就等于掌握了异步编程，这实际上是对异步编程的一大误解。消息驱动本质上只是任务的驱动手段之一，它
      * 身并不带来真正的异步，异步的关键并不在你以什么方式去调用另一个任务，更重要的是你如何管理异步任务的生命周
      * 期。如果我们不能了解这一点，那么消息驱动将仅仅停留在表面，只是作比为传统函数调用更酷的的替代品，并不能构建
      * 出正真的异步程序。为此我以 akka 为平台来大致讲解一下什么才真正的异步驱动。
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
      * 以上这段代码我们用 akka 系统实现了消息驱动，通过一个 actor 得到问候语。现在我们从架构的角度来思考一下它的价值
      * 有多大？很遗憾，答案是这个实现毫无价值，连“几乎”都配不上。(akka 的分布式附加值不是我们本次考察的重点）。为什么呢？
      * 为了简化问题，我们现在抛开 akka，仅以纯粹的多线程来实现这个例子中的任务模型。
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
      * 在上面这个例子中我们用了一个 Future 模型来取代 akka，我们可以很清楚地看到两者的的线程调度模式其实是一样的。
      * 都以主线程启动了一个任务线程来生成问候语，然后主线程等待任务线程执行结束后主线程打印出问候语。在这个例子中
      * 我们甚至连消息驱动都没有使用就实现了同样的线程调度。也就是说消息驱动仅仅就线程模型来说是可有可无的。所以消
      * 息驱动并不是异步编程的充分条件。甚至，如果你更细心一些，你还会产生疑问，以上两个例子根本不能称为异步架构，
      * 最多只是一个普通的多线程架构。更甚至，这个多线程架构相对于传统单线程架构来说，也毫无优势。我们完全可以
      * 将任务线程和主线程合并，只用一个主线程既生成问候语，又打印问候语，在架构的价值上也是完全等价的，因为在工
      * 作线程执行的过程中，主线程是空闲的，在这期间它完全可以不需要工作线程（如果不考虑分布的话）取而代之。难
      * 道不是么？
      *
      * 有关价值的判断标准，业务价值其实是一个最根本的标准，借用领域驱动的概念，它是问题域的范畴，而解决方案是答
      * 案域，如果没有问题，那么就没必要有答案。所以如果单线程能够解决问题，那么多线程方案就形同脱裤子放屁。所以这
      * 样无端的多线程就毫无价值，但是我们的程序员却经常犯这样的错误。接下来我将以一个乒乓球比赛的案例，来讲解异步
      * 编程是如何在既不无中生有地解决不存在的需求的前提下，满足整体性能提升的（假设对性能的追求属于问题之一）。
      *
      * 在乒乓球比赛中，选手A和选手B相当于两个线程，互相击球就相当于互相调用，当选手A将球击打给B后，任务就交给了
      * 选手B，在B回球的过程中，选手A其实处于等待状态。这在现实比赛中习以为常，但是在电脑的CPU中，这是极大的资
      * 源浪费（问题），如果选手A的速度足够快，他完全可以回家吃个饭再回来，没有必要专门等待对手的回球。甚至他够
      * 敬业的话，他甚至他可以跑到球桌的对面去回击自己发出的球，那么他一个人（线程）就可以完成这场比赛，根本不需
      * 要选手B。为此我们有必要来澄清一下有关异步的几个误区：
      *
      * 误区一：消息驱动是异步编程重要的任务驱动手段，但不是异步架构的充分条件。
      * 误区二：真正的异步架构其实并不刻意追求多线程（避免创造无端问题），它追求的是最有效的CPU利用率（解决性能
      *。      问题）。
      *
      * 那么怎样才是最有效的CPU利用率呢？让我们还是以乒乓球比赛为例。但是我们现在要改一下规则，现在我们增加一个
      * 称为“选手池”的东西，它用来存放没有上场的选手，其次、我们也不再区分你我阵营，所有的选手都来自这个“选手
      * 池”，还有，我们也不再规定开球的选手只能在他的发球侧打比赛，甚至不要求他在开出球之后必须继续在球桌边等待
      * 对方的回球。比赛的评判标准仅仅以乒乓球能够在球桌两边被不断被击打为唯一评判标准。也就是说，我们关注的是
      * 业务能够被持续执行下去，而不关注由多少线程，以什么样的方式，在何时何地执行下去。那么我们将可能会看到这样一
      * 种比赛场景，选手A发出球后就离开了比赛现场回到“选手池”等待下一个调配去了，而选手B在回球后也离开了现场打电话
      * 去了，当球被打回到发球侧的时候，选手C恰逢其时地出现了，他替已经下场的选手A将球击回了另一边，而球在另一边
      * 即将落地前，选手A及时回到了球桌的另一边又将球击了回来……。
      *
      * 甚至如果他们三人的速度足够块的话，就能够以同样的方式同时进行多场比赛！是的，我们以某种方式将原本两人玩一场
      * 比赛的模式扩展成了三人同时玩多场比赛。而且，更重要的是，不管比赛有多激烈，任何一场比赛中同时只有一个选手在
      * 场！是的！虽然在场的可能不是同一个选手，但是当一个选手在击球的时候，另外两个选手不是在休息区就是在另外一个
      * 比赛场上！他们绝不会同时出现在同一个场地中去等待任何其他人击球！也就是说，虽然这是个多线程的程序，但是因为
      * 他们串列执行，使得每个业务主线都等价于于单线程！回顾一下前面有关领域驱动的段落，我们避免了用无端的多线程来
      * 执行单线程就可以满足的需求。但是解决了服务超过总线程数的任务的需求！因为我们充分地利用了选手的等待时间，
      * 这样极大地利用了有限的选手，完成了更多的击球动作，整体比赛效率因此得到提升。
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
          * Thread-23: Ball 93 with 962 hits!
          * Thread-23: Ball 62 with 962 hits!
          * Thread-23: Ball 3 with 965 hits!
          * Thread-23: Ball 78 with 962 hits!
          * Thread-21: Ball 23 with 963 hits!
          * Thread-21: Ball 89 with 962 hits!
          * Thread-21: Ball 69 with 962 hits!
          * Thread-21: Ball 100 with 964 hits!
          * Thread-21: Ball 64 with 962 hits!
          * Thread-21: Ball 24 with 963 hits!
          *
          *
          * 你可能看到的和我略有不同，但是我们可以很清晰地看到总共只有三个工作线程在工作，并且他们都随机地出现在任何一
          * 场比赛中，每个球的击打总次数甚至高达近 1000 次！也就是说我们只用三个并行线程就完成了 100 个并发任务，并且
          * 每个任务被调度了近 1000 次！过程中没有任何一个线程在执行任何一项任务时等待任何一个其他调度的完成，但是即便
          * 如此繁忙，也没有任何一颗球落在地上。在这个例子中我们可以对异步多任务系统总结以下几点军规：
          *
          * 一、拒绝在线程中使用任何等待操作（最后的 sleep 除外），不必惧怕任何线程调度的结束，并且应该欢迎线程尽早结
          *    束。只有死的（结束）的线程才是好的线程，任何半死不活（等待）的线程都是耍流氓。
          * 二、线程并不是越多越好，多余的线程如果没有机会参与任务调度，那么并不能提高整体效率。笔者将线程数设置为与这
          *    台试验机内核数相同的 24 个后再次执行得到的结果甚至略低于 3 线程。
          * 三、对线程的请求也不是越少越好，请求多少线程并不意味着就能得到多少线程，如果池子里只有3 个，即便请求 10000
          *    次，最多时也只有3 个线程在运行，多出来的请求都压在队列里，只不过多消耗了一点点内存而已，并不会给系统带
          *    来多少压力。
          * 四、Future 不是线程，Future 只是一张船票（线程请求存根），得到一张 Future 意味着你“将”会登上某条船（线程），
          *    但你什么时候能登上取决于队列的长度和吞吐率。
          * 五、消息驱动是队列的一种驱动模型，与线程调度属于不同的范畴，消息驱动解决的是任务的解耦和分布能力，同时消息
          *    可能带来的负面影响是通道的吞吐能力，消息通道本身可能是会带锁的，因此它本身的设计是个独立话题。
          * 六、akka actor 是业务执行对象，是船（线程）上的乘客，线程只管带着它跑一圈，线程本身不参与 actor 的任何行为，
          *    因此它的多少与线程没有关系，在这个例子中，无论多少个任务，多少个线程，都复用了同一个 actor。
          * 七、系统里唯一对线程数、线程调度方式、调度效率等等因数有影响的东西是线程池，除此之外的任何担忧都是多此一举。
          *
          * 我们通过这样一个双边（乒乓）系统演示了以上七条军规，但是它只是异步模型中的一个最小单位，真实的业务模型通常
          * 是多边模型，复杂程度会远远超过这个案例，实施的复杂度和要考虑的因数也远远超过本例，但是不管多么复杂，以上这
          * 几条都是你继续出发的基础。最后附上这个程序的 Future 模型，在这模型中因为没有消息瓶颈，每个线程被调度的次
          * 数甚至可以高达 1200 次以上！但是程序也因此失去了分布式部署的能力，程序的分布部署能力是另一个话题领域，本文
          * 不打算做详细介绍。
          * */
    }

    it should "similar to pure Future model" in {
        /** 新建一个线程，其中有 3 个 player */
        implicit val player: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(3))

        /** 定义击球动作（业务方法） */
        def playGame: Hit => Future[Hit] = hit => Future {
            println(s"Thread-${Thread.currentThread.getId}: Ball ${hit.ball} with ${hit.count} hits!")
            Hit(hit.ball, hit.count+1)
        }.map { playGame }.asInstanceOf[Future[Hit]]

        /** 同时开出 100 个球（对应 100 场比赛） */
        for (i <- 1 to 100) yield playGame(Hit(i,1))

        /** 让游戏持续 1 秒 */
        Thread.sleep(1000)
    }

    /**
      * 最后，以一个线程或者少数线程服务于多于线程数的任务，并且保持任务不停顿的模型有一个专有称呼，叫做“协程”。协程
      * 模型其实就是一种特定的异步驱动模型。也因此 AKKA 系统可以被作为实现 SCALA 协程的框架。
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

