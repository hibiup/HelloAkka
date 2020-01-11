package com.hibiup.samples.helloakka

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

// 主程序
object IotApp {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("iot-system")

    try {
      // Create top level supervisor
      val supervisor = system.actorOf(IotSupervisor.props(), "iot-supervisor")
      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      Await.result(system.terminate(), 10 second)
    }
  }
}


// 1) 根节点
object IotSupervisor {
  def props(): Props = Props(new IotSupervisor)
}

class IotSupervisor extends Actor with ActorLogging {  // ActorLogging 包含 log 对象。
  override def preStart(): Unit = log.info("IoT Application started")
  override def postStop(): Unit = log.info("IoT Application stopped")

  // 这个根节点不需要处理任何消息，因此将它设置为一个空actor(哑终端)
  override def receive = Actor.emptyBehavior
}


// 2）设备节点
object Device {
  // Device actor 的构造工厂
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  // 消息类
  //1) 读取/返回温度
  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])

  // 2) 设置温度及其返回消息
  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  var lastTemperatureReading: Option[Double] = None  // 内部温度变量

  override def preStart(): Unit = log.info("Device actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("Device actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case RecordTemperature(id, value) =>
      log.info("Recorded temperature reading {} with {}", value, id)
      lastTemperatureReading = Some(value)  // 设置内部温度变量
      sender() ! TemperatureRecorded(id)    // 返回成功消息

    case ReadTemperature(id) =>
      sender() ! RespondTemperature(id, lastTemperatureReading)  // 读取内部温度变量
  }
}