package io.altoo.serialization.kryo.pekko

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.testkit.AbstractPekkoTest
import org.apache.pekko.serialization.{ByteBufferSerializer, SerializationExtension}

import java.nio.ByteBuffer
import scala.util.Try

object BasicSerializationTest {

  private val config =
    s"""
       |pekko {
       |  loggers = ["org.apache.pekko.event.Logging$$DefaultLogger"]
       |  loglevel = "WARNING"
       |
       |  actor {
       |    serializers {
       |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
       |    }
       |
       |    serialization-bindings {
       |      "scala.Product" = kryo
       |    }
       |  }
       |}
       |
       |pekko-kryo-serialization {
       |  trace = true
       |  id-strategy = "incremental"
       |  implicit-registration-logging = true
       |  post-serialization-transformations = off
       |}
       |""".stripMargin
}

class BasicSerializationTest extends AbstractPekkoTest(ConfigFactory.parseString(BasicSerializationTest.config)) {
  private val serialization = SerializationExtension(system)

  private val testList = List(1 to 40: _*)

  behavior of "KryoSerializer"

  it should "be selected for lists" in {
    // Find the Serializer for it
    val serializer = serialization.findSerializerFor(testList)
    serializer.getClass.equals(classOf[PekkoKryoSerializer]) shouldBe true

    // Check serialization/deserialization
    val serialized = serialization.serialize(testList)
    serialized shouldBe a[util.Success[?]]

    val deserialized = serialization.deserialize(serialized.get, testList.getClass)
    inside(deserialized) {
      case util.Success(v) => v shouldBe testList
    }

    // Check buffer serialization/deserialization
    serializer shouldBe a[ByteBufferSerializer]
    val bufferSerializer = serializer.asInstanceOf[ByteBufferSerializer]

    val bb = ByteBuffer.allocate(testList.length * 8)

    val bufferSerialized = Try(bufferSerializer.toBinary(testList, bb))
    bufferSerialized shouldBe a[util.Success[?]]
    bb.flip()
    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, testList.getClass.getName))
    inside(bufferDeserialized) {
      case util.Success(v) => v shouldBe testList
    }

  }
}
