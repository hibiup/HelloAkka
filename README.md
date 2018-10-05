# Akka 2.5 教程案例
https://doc.akka.io/docs/akka/2.5/guide/tutorial_1.html

## pi

该例子演示一个以 "embarrassingly parallel" 方式计算 pi 值。

embarrassingly parallel 的意识是所有的运算子都完全独立，彼此之间没有任何关联。

该应用的 actor 将在多 CPU 内核的处理器上得到分发，并且下一步将进一步被分发到集群的不同主机上。

### 参考地址：
 教程参考地址：https://doc.akka.io/docs/akka/2.0/intro/getting-started-first-scala.html

 项目源 GIT 地址：git://github.com/akka/akka.git

 Akka官方文档参考地址：https://doc.akka.io/docs/akka/current/guide/actors-intro.html

### 教程资源
 pi 的计算公式：![](src/main/resources/pi-formula.png)

### 项目架构
* 一个 Master actor 初始化计算
* 多个 Worker actor 分担计算工作，并将结果返回给 Master
* 主监听进程(Listener)来自 Master actor 的结果并打印出来。

为此我们需要四个不同的消息：

* Calculate - 发送给 Master actor 令其开始任务的消息
* Work - 由 Master actor 发出给 Work actor的消息，包含了分工的内容。
* Result - 由 Worker actor 返回给 Master actor 的包含计算结果的消息。
* PiApproximation - Master actor 发送给 Listener 的包括 pi 值和计算时长的消息。

消息设计细节：

* 发送给 actor 的消息必须是 immutable 的，mutable 会导致状态共享。
* case class 非常适合用来编写消息
* 设计一个trait作为所有消息的基类(com.hibiup.samples.helloaka.PiMessage)

## Sample01:

一个简单的案例。

## Sample02:

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

## Sample03:

这个例子以一个 Iot 应用为例，参考地址：https://doc.akka.io/docs/akka/2.5/guide/tutorial_2.html
![](src/main/resources/arch_tree_diagram.png)

### Actor 测试
https://doc.akka.io/docs/akka/2.5/testing.html

### 消息类

* 一个读取温度的 actor 负责查询并返回当前的温度，为此它需要两个消息体：
    * ReadTemperature     请求消息
    * RespondTemperature  返回消息

Akka 的消息缺省投递最多一次，并且不保证送达。

* 最多一次： 0 到 1 次
* 最少一次： 可能多次，直到至少一条到达
* 恰好一次： 保证，且只投递一次，但是不保证到达

消息的投递由 akka 的消息子系统完成。它们的传递按时间顺序排列，根据不同的发送者，各自有各得队列。例如假设：
    Actor A1 sends messages M1, M2, M3 to A2.
    Actor A3 sends messages M4, M5, M6 to A2.
This means that, for Akka messages:
    If M1 is delivered it must be delivered before M2 and M3.
    If M2 is delivered it must be delivered before M3.
    If M4 is delivered it must be delivered before M5 and M6.
    If M5 is delivered it must be delivered before M6.
A2 can see messages from A1 interleaved with messages from A3. Since there is no guaranteed 
delivery, any of the messages may be dropped, i.e. not arrive at A2.

### actor 类

### device actor
