package io.altoo.serialization.kryo.pekko.typed.serializer

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.serialization.SerializationExtension
import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.PekkoKryoSerializer
import io.altoo.serialization.kryo.pekko.typed.testkit.AbstractTypedPekkoTest

object TypedActorRefSerializerTest {
  private val testConfig =
    """
      |pekko {
      |  actor {
      |    serializers {
      |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
      |    }
      |    serialization-bindings {
      |      "org.apache.pekko.actor.typed.ActorRef" = kryo
      |      "org.apache.pekko.actor.typed.internal.adapter.ActorRefAdapter" = kryo
      |    }
      |  }
      |}
      |pekko-kryo-serialization {
      |  trace = true
      |  id-strategy = "default"
      |  implicit-registration-logging = true
      |  post-serialization-transformations = off
      |}
      |""".stripMargin

  private trait Msg
}

class TypedActorRefSerializerTest extends AbstractTypedPekkoTest(ConfigFactory.parseString(TypedActorRefSerializerTest.testConfig)) {
  import TypedActorRefSerializerTest.*

  private val serialization = SerializationExtension(testKit.system.classicSystem)

  behavior of "TypedActorRefSerializer"

  it should "serialize and deserialize actorRef" in {
    val value: ActorRef[Msg] = testKit.spawn(Behaviors.ignore[Msg])

    // serialize
    val serializer = serialization.findSerializerFor(value)
    serializer shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(value)
    serialized shouldBe a[util.Success[?]]

    // deserialize
    val deserialized = serialization.deserialize(serialized.get, classOf[ActorRef[Msg]])
    deserialized shouldBe util.Success(value)
  }
}
