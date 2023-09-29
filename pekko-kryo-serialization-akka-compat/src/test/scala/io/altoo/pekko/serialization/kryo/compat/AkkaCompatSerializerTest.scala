package io.altoo.pekko.serialization.kryo.compat

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.PekkoKryoSerializer
import io.altoo.testing.SampleMessage
import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Inside}

object AkkaCompatSerializerTest {
  private val testConfig =
    """
      |pekko {
      |  actor {
      |    serializers {
      |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
      |    }
      |    serialization-bindings {
      |      "org.apache.pekko.actor.ActorRef" = kryo
      |      "akka.actor.ActorRef" = kryo
      |      "akka.actor.ActorRef" = kryo
      |      "io.altoo.testing.SampleMessage" = kryo
      |    }
      |  }
      |}
      |pekko-kryo-serialization {
      |  trace = true
      |  id-strategy = "default"
      |  implicit-registration-logging = true
      |  post-serialization-transformations = off
      |
      |  kryo-initializer = "io.altoo.pekko.serialization.kryo.compat.AkkaCompatKryoInitializer"
      |}
      |""".stripMargin

  // serialized io.altoo.testing.SampleMessage(actorRef: akka.actor.ActorRef) with akka-kryo-serialization
  private val akkaActorRefSerialized = Array[Byte](1, 0, 105, 111, 46, 97, 108, 116, 111, 111, 46, 116, 101, 115, 116, 105, 110, 103, 46, 83, 97, 109, 112, 108, 101, 77, 101, 115, 115, 97,
    103, -27, 1, 1, 1, 97, 107, 107, 97, 46, 97, 99, 116, 111, 114, 46, 82, 101, 112, 111, 105, 110, 116, 97, 98, 108, 101, 65, 99, 116, 111, 114, 82, 101, -26, 1, 97, 107, 107, 97, 58, 47,
    47, 116, 101, 115, 116, 83, 121, 115, 116, 101, 109, 47, 117, 115, 101, 114, 47, 115, 97, 109, 112, 108, 101, 65, 99, 116, 111, 114, 35, 49, 53, 56, 51, 48, 57, 56, 56, 51, -75)
}

class AkkaCompatSerializerTest extends TestKit(ActorSystem("testSystem", ConfigFactory.parseString(AkkaCompatSerializerTest.testConfig).withFallback(ConfigFactory.load())))
    with AnyFlatSpecLike with Matchers with Inside with BeforeAndAfterAll {

  private val serialization = SerializationExtension(system)

  override protected def afterAll(): Unit = shutdown(system)

  behavior of "ActorRefSerializer"

  it should "serialize and deserialize actorRef" in {
    // create actor with path to not get deadLetter ref
    system.actorOf(Props(new Actor { def receive: Receive = PartialFunction.empty }), "sampleActor")

    val serializer = serialization.serializerFor(classOf[SampleMessage])
    serializer shouldBe a[PekkoKryoSerializer]

    // deserialize
    val deserialized = serializer.fromBinary(AkkaCompatSerializerTest.akkaActorRefSerialized)
    deserialized shouldBe a[SampleMessage]
    deserialized.asInstanceOf[SampleMessage].actorRef.path.toString shouldBe "pekko://testSystem/user/sampleActor"
  }
}
