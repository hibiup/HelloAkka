# 介绍
该例子演示一个以 "embarrassingly parallel" 方式计算 pi 值。

embarrassingly parallel 的意识是所有的运算子都完全独立，彼此之间没有任何关联。

该应用的 actor 将在多 CPU 内核的处理器上得到分发，并且下一步将进一步被分发到集群的不同主机上。

# 参考地址：
 教程参考地址：https://doc.akka.io/docs/akka/2.0/intro/getting-started-first-scala.html

 项目源 GIT 地址：git://github.com/akka/akka.git

 Akka官方文档参考地址：https://doc.akka.io/docs/akka/current/guide/actors-intro.html

# 教程资源
 pi 的计算公式：![](src/main/resources/pi-formula.png)

# 项目架构
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
