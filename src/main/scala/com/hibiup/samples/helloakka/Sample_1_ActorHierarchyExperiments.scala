package com.hibiup.samples.helloakka


package Sample_1_ActorHierarchyExperiments {
    /**
      * Actor 以树状结构管理：https://doc.akka.io/docs/akka/2.5/guide/tutorial_1.html
      *
      * 整个系统中只有一个根节点（/），下面有两个分节点，一个是 /user，还有一个是 /system。所有的用户应用都挂在 /user 下。
      *
      * */
    import akka.actor.{Actor, ActorSystem, Props}

    object ActorHierarchyExperiments {
        def apply() = {
            /** 1-1) 初始化一个 Actor system (akka://testSystem/)*/
            val system = ActorSystem("testSystem")

            /** 1-2) 通过 system.actorOf (在 /user 下)生成应用的顶级节点。生成的参考模板（类）是 PrintMyActorRefActor */
            val firstRef = system.actorOf(Props[PrintMyActorRefActor], "first-actor")
            println(s"First: $firstRef")

            /** 1-3) 向该节点发送一个消息 */
            firstRef ! "printit"

            /** 1-4) 模拟错误 */
            firstRef ! "fail"

            /** 1-5) 删除节点 */
            firstRef ! "stop"


            /** 4) 删除 ActorSystem */
            system.terminate()
        }
    }

    // Actor 模板, 需要重载 Actor.receive 来接收消息
    class PrintMyActorRefActor extends Actor {
         override def receive: Receive = {
             /** 2-1) 接受到消息 */
            case "printit" ⇒
                /** 3) 通过 context.actorOf() 生成一个新的子节点。 context 是个隐式 ActorContext 实例，它保存有 actor
                  *    树的上下文，通过它来加入子节点。*/
                val secondRef = context.actorOf(Props[PrintMyActorRefActor], "second-actor")
                println(s"Second: $secondRef")

            /** 2-2) 模拟输出错误 */
            case "fail" ⇒
                println("ACTOR FAILS NOW!")
                throw new Exception("I failed!")

            /** 2-3) 如果收到 stop，就删除自己．(会连同删除以下子节点, 父子 actor　被删除时逐个输出 stopped) ．*/
            case "stop" ⇒ context.stop(self)
        }
        override def preStart(): Unit = println(s"[started] $this")
        override def postStop(): Unit = println(s"[stopped] $this")
    }
}
