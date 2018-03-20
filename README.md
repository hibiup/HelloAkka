# Sample02:

这个例子基于Akka 2.5

参考地址：https://doc.akka.io/docs/akka/2.5/guide/tutorial_1.html

摘要：
akka 在系统中维护了一个树状的 actor 管理结构，通常一个根 actor 点必须通过 system.actorOf() 来生成，其下的子 actor 必须通过 context.actorOf() 来生成，
这样它们才会被加载到整个树中。
![](src/main/resources/actor_top_tree.png)

* / - 所以有actor 的根节点
* /user - 所有用户actor空间的根节点
* /system - 系统监控 actor 的根节点

通常我们应该将 actor 建在 /user 下，通过 ActorSystem("name") 的方式初始化获得 system ，然通过 system.actorOf()
添加第一个节点。新建的节点内部缺省存在一个 context 实例，指向节点的上下文，通过调用这个 context 挂接下一个节点。