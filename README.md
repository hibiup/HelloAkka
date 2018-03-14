该例子演示一个以 "embarrassingly parallel" 方式计算 pi 值。

embarrassingly parallel 的意识是所有的运算子都完全独立，彼此之间没有任何关联。

该应用的 actor 将在多 CPU 内核的处理器上得到分发，并且下一步将进一步被分发到集群的不同主机上。

pi 的计算公式：![](src/main/resources/pi-formula.png)
