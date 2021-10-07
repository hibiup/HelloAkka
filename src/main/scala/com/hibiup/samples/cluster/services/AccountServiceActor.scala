package com.hibiup.samples.cluster.services

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.stream.Materializer
import com.hibiup.samples.cluster.{AccountServiceRegistration, Message, Result}

object AccountServiceActor{
    def apply(system:ActorSystem, mat:Materializer, name:String = "AccountService") = system.actorOf(Props[AccountServiceActor](), name = name)
}

/**
 * 后端 Actor
 */
class AccountServiceActor extends Actor with ActorLogging {
    /**
     * 1）获得当前的 Cluster
     */
    val cluster = Cluster(context.system)

    // subscribe to cluster changes, re-subscribe when restart
    override def preStart(): Unit = {
        /**
         * 2）Actor 会自动将自己注册到集群，但是我们可以通过重载 preStart 来选择希望接收到哪些集群消息。例如以下参数将
         * 让我们接收到 MemberEvent（其他成员加入或退出的）消息 和 UnreachableMember（失去相应的成员）消息。
         *
         * 其中 MemberUp，MemberRemoved，UnreachableMember 消息允许我们监听其它有关 Actor 的状态，以便该 Actor
         * 判断与它们的通讯能力。(参见 FrontendActor 的说明)
         */
        cluster.subscribe(
            self,
            initialStateMode = InitialStateAsEvents, classOf[MemberRemoved], classOf[MemberUp], classOf[UnreachableMember], classOf[MemberEvent]
        )
    }

    /**
     * 可选：退出时将自己从集群中注销。可以添加其他代码，比如清除本地资源等
     */
    override def postStop(): Unit = cluster.unsubscribe(self)

    def receive = {
        /**
         * 当一个新的节点（Node）就绪时，会广播 MemberUp 信息，通过监听该信息，我们可以决定是否向其注册本 Actor。
         */
        case MemberUp(member) =>
            log.info("Member is Up: {}", member.address)
            /**
             * 查询该节点中是否存在某个特定的 Actor（Role='FRONTEND'），如果存在，则向它发送'注册'消息。
             *
             * 注册消息可以为空（也可以不为空），当上游接收到注册消息的同时，也会获得本 Actor 的存根（sender），它们可以将该存根保留
             * 以备以后通讯使用。
             * */
            register(member)

        /**
         * MemberUp 用于接收后于本节点启动的节点消息，而 CurrentClusterState 则可以获得先于本节点启动的其他节点的列表。
         * 当一个 Actor 注册成功后会收到来自该 Cluster 的 CurrentClusterState 消息，可以通过该消息获得节点的其它已经就绪的成员，并
         * 发送自己的注册消息。
         */
        case state: CurrentClusterState =>
            state.members.filter(_.status == MemberStatus.Up).foreach(register)

        /**
         * 处理来自上游的消息
         */
        case Message(text) =>
            sender() ! Result(text.toUpperCase)

        // 处理其他消息，例如成员节点下线消息等。。。
        case UnreachableMember(member) =>
            log.info("Member detected as unreachable: {}", member)

        case MemberRemoved(member, previousStatus) =>
            log.info("Member is Removed: {} after {}", member.address, previousStatus)

        case memEvent: MemberEvent =>
            log.info("Received member event: " + memEvent.toString)
    }

    def register(member: Member): Unit =
        //if (member.hasRole("FRONTEND"))
            context.actorSelection(RootActorPath(member.address) / "user" / "Frontend") ! AccountServiceRegistration
}
