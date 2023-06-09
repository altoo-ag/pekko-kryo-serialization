package io.altoo.serialization.kryo.pekko.serializer

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.PekkoKryoSerializer
import io.altoo.serialization.kryo.pekko.testkit.AbstractPekkoTest
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.util.{ByteString, CompactByteString}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

object ByteStringSerializerTest {
  private val config =
    """
      |pekko {
      |  actor {
      |    serializers {
      |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
      |    }
      |    serialization-bindings {
      |      "org.apache.pekko.util.ByteString$ByteString1C" = kryo
      |      "org.apache.pekko.util.ByteString" = kryo
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
}

class ByteStringSerializerTest extends AbstractPekkoTest(ConfigFactory.parseString(ByteStringSerializerTest.config)) with AnyFlatSpecLike with Matchers {
  private val serialization = SerializationExtension(system)


  behavior of "ByteStringSerializer"

  it should "handle ByteStrings" in {
    val value = ByteString("foo")

    // serialize
    val serializer = serialization.findSerializerFor(value)
    serializer shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(value)
    serialized.isSuccess shouldBe true

    // deserialize
    val deserialized = serialization.deserialize(serialized.get, classOf[ByteString])
    deserialized.isSuccess shouldBe true
    deserialized.get shouldBe value
  }

  it should "handle compact ByteStrings" in {
    val value = ByteString("foo").compact

    // serialize
    val serializer = serialization.findSerializerFor(value)
    serializer shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(value)
    serialized.isSuccess shouldBe true

    // deserialize
    val deserialized = serialization.deserialize(serialized.get, classOf[CompactByteString])
    deserialized.isSuccess shouldBe true
    deserialized.get shouldBe value
  }
}
