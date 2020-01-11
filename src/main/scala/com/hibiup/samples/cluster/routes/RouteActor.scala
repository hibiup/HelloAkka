package com.hibiup.samples.cluster.routes

import akka.actor.{Actor, ActorRef, Terminated}
import com.hibiup.samples.cluster.{AccountServiceRegistration, Message, Result}


/**
 * 负责和后端通讯的 Actor
 */
class RouteActor extends Actor{
    var backends = IndexedSeq.empty[ActorRef]
    var jobCounter = 0

    /**
     * 处理来自后端的消息
     */
    def receive = {
        /**
         * 首先需要处理的是后端的注册请求。
         *
         * 上下游 Actor 之间需要互相取得对方的 stub（sender） 然后通过 stub 进行通讯。上游节点注册消息会通过 'MemberUp' 进行广播，下游
         * Actor 通过监听该消息取得节点，进而获得节点上注册的 Actor 的 Ref，然后向上游 actor 发送一个空的'注册'消息，上游 Actor 接
         * 收到'注册'消息后，将 sender 保存在本地列表中即可以后复用。（参见 AccountServiceActor）
         */
        case AccountServiceRegistration =>
            if(!backends.contains(sender())) {
                context.watch(sender())
                backends = backends :+ sender()
            }

        /**
         * 处理任务，如果尚无后端注册，则返回失败。
         */
        case msg: Message if backends.isEmpty =>
            sender() ! Result("Backend service is unavailable, try again later")

        /**
         * 否则从本地的下游注册列表中取出任意一个，将任务转发过去。
         */
        case job: Message =>
            jobCounter += 1
            backends(jobCounter % backends.size).forward(job)

        // 因为 Frontend 使用的是阻塞模式，因此不会从这里接收到回复消息。
        case resp: Result => context.system.log.info("Receive result from backend service: " + resp.msg)

        case Terminated(a) => backends = backends.filterNot(_ == a)
    }
}
