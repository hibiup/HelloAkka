package com.hibiup.samples.helloakka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.hibiup.samples.helloakka.Device._
import org.scalatest._

/**
  * Created by 326487162 on 2018/03/21.
  */
class Example_3_Iot_test extends TestKit(ActorSystem("TestSpec"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
    override def afterAll =
    {
      TestKit.shutdownActorSystem(system)
    }

    "首次调用返回None" in {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device")) // 调用工厂类返回 actorOf

      // tell 方法等于 "!"，但是需要显式提供 sender，这里也就是 probe.ref。
      deviceActor.tell(ReadTemperature(requestId = 42), probe.ref)

      // expectMsgType TestProbe 的 received 消息句柄
      val response = probe.expectMsgType[RespondTemperature]
      response.requestId should ===(42)
      response.value should ===(None)  // 首次调用返回的温度是 None
    }

  it should {

  }

    "返回最后一次设置的温度" in {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(Device.RecordTemperature(requestId = 1, 24.0), probe.ref)
      probe.expectMsg(Device.TemperatureRecorded(requestId = 1))

      deviceActor.tell(Device.ReadTemperature(requestId = 2), probe.ref)
      val response1 = probe.expectMsgType[Device.RespondTemperature]
      response1.requestId should ===(2)
      response1.value should ===(Some(24.0))

      deviceActor.tell(Device.RecordTemperature(requestId = 3, 55.0), probe.ref)
      probe.expectMsg(Device.TemperatureRecorded(requestId = 3))

      deviceActor.tell(Device.ReadTemperature(requestId = 4), probe.ref)
      val response2 = probe.expectMsgType[Device.RespondTemperature]
      response2.requestId should ===(4)
      response2.value should ===(Some(55.0))
    }
  }

