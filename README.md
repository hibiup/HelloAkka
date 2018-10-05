# Sample03:

这个例子以一个 Iot 应用为例，参考地址：https://doc.akka.io/docs/akka/2.5/guide/tutorial_2.html
![](src/main/resources/arch_tree_diagram.png)

# Actor 测试
https://doc.akka.io/docs/akka/2.5/testing.html

## 消息类

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

## actor 类

### device actor
