package io.altoo.serialization.kryo.pekko

import org.apache.pekko.serialization.{ByteBufferSerializer, Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.testkit.AbstractPekkoTest
import scala.annotation.nowarn
import java.nio.ByteBuffer
import scala.collection.immutable.{HashMap, TreeMap}
import scala.collection.mutable.AnyRefMap
import scala.util.Try

object TransformationSerializationTest {
  private val defaultConfig =
    """
      |pekko {
      |  actor {
      |   allow-java-serialization = off
      |   serializers {
      |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
      |    }
      |    serialization-bindings {
      |      "java.io.Serializable" = none
      |      "scala.collection.immutable.TreeMap" = kryo
      |      "[Lscala.collection.immutable.TreeMap;" = kryo
      |      "scala.collection.immutable.HashMap" = kryo
      |      "[Lscala.collection.immutable.HashMap;" = kryo
      |      "scala.collection.immutable.HashSet" = kryo
      |      "[Lscala.collection.immutable.HashSet;" = kryo
      |      "scala.collection.immutable.TreeSet" = kryo
      |      "[Lscala.collection.immutable.TreeSet;" = kryo
      |      "scala.collection.mutable.HashMap" = kryo
      |      "[Lscala.collection.mutable.HashMap;" = kryo
      |      "scala.collection.mutable.AnyRefMap" = kryo
      |      "[Lscala.collection.mutable.AnyRefMap;" = kryo
      |      "scala.collection.mutable.HashSet" = kryo
      |      "[Lscala.collection.mutable.HashSet;" = kryo
      |      "scala.collection.mutable.TreeSet" = kryo
      |      "[Lscala.collection.mutable.TreeSet;" = kryo
      |    }
      |  }
      |}
      |pekko-kryo-serialization {
      |  type = "nograph"
      |  id-strategy = "incremental"
      |  kryo-reference-map = false
      |  buffer-size = 65536
      |  post-serialization-transformations = off
      |  implicit-registration-logging = true
      |  encryption {
      |    aes {
      |      key-provider = "io.altoo.serialization.kryo.scala.DefaultKeyProvider"
      |      mode = "AES/GCM/NoPadding"
      |      iv-length = 12
      |      password = "j68KkRjq21ykRGAQ"
      |      salt = "pepper"
      |    }
      |  }
      |}
      |""".stripMargin
}

class ZipTransformationSerializationTest extends TransformationSerializationTest("Zip", "pekko-kryo-serialization.post-serialization-transformations = deflate")
class Lz4TransformationSerializationTest extends TransformationSerializationTest("LZ4", "pekko-kryo-serialization.post-serialization-transformations = lz4")
class AESTransformationSerializationTest extends TransformationSerializationTest("AES", "pekko-kryo-serialization.post-serialization-transformations = aes")
class ZipAESTransformationSerializationTest extends TransformationSerializationTest("ZipAES", """pekko-kryo-serialization.post-serialization-transformations = "deflate,aes"""")
class LZ4AESTransformationSerializationTest extends TransformationSerializationTest("LZ4AES", """pekko-kryo-serialization.post-serialization-transformations = "lz4,aes"""")
class OffTransformationSerializationTest extends TransformationSerializationTest("Off", "")
class UnsafeTransformationSerializationTest extends TransformationSerializationTest("Unsafe", "pekko-kryo-serialization.use-unsafe = true")
class UnsafeLZ4TransformationSerializationTest extends TransformationSerializationTest("UnsafeLZ4",
  """
    |pekko-kryo-serialization.use-unsafe = true
    |pekko-kryo-serialization.post-serialization-transformations = lz4
    """.stripMargin
)

@nowarn("cat=deprecation")
abstract class TransformationSerializationTest(name: String, config: String) extends AbstractPekkoTest(
  ConfigFactory.parseString(config)
      .withFallback(ConfigFactory.parseString(TransformationSerializationTest.defaultConfig))
) {
  private val serialization: Serialization = SerializationExtension(system)


  behavior of s"$name transformation serialization"

  it should "serialize and deserialize immutable TreeMap[String,Any] successfully" in {
    val tm = TreeMap[String, Any](
      "foo" -> 123.3,
      "bar" -> "something as a text",
      "baz" -> null,
      "boom" -> true,
      "hash" -> HashMap[Int, Int](1 -> 200, 2 -> 300, 500 -> 3))

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[TreeMap[String, Any]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[TreeMap[String, Any]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize immutable HashMap[String,Any] successfully" in {
    val tm = HashMap[String, Any](
      "foo" -> 123.3,
      "bar" -> "something as a text",
      "baz" -> null,
      "boom" -> true,
      "hash" -> HashMap[Int, Int](1 -> 200, 2 -> 300, 500 -> 3))

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[HashMap[String, Any]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[HashMap[String, Any]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize mutable AnyRefMap[String,Any] successfully" in {
    val r = new scala.util.Random(0L)
    val tm = AnyRefMap[String, Any](
      "foo" -> r.nextDouble(),
      "bar" -> "foo,bar,baz",
      "baz" -> 124L,
      "hash" -> HashMap[Int, Int](r.nextInt() -> r.nextInt(), 5 -> 500, 10 -> r.nextInt()))

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[AnyRefMap[String, Any]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[AnyRefMap[String, Any]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize mutable HashMap[String,Any] successfully" in {
    val tm = scala.collection.mutable.HashMap[String, Any](
      "foo" -> 123.3,
      "bar" -> "something as a text",
      "baz" -> null,
      "boom" -> true,
      "hash" -> HashMap[Int, Int](1 -> 200, 2 -> 300, 500 -> 3))

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[scala.collection.mutable.HashMap[String, Any]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[scala.collection.mutable.HashMap[String, Any]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  // Sets
  it should "serialize and deserialize immutable HashSet[String] successfully" in {
    val tm = scala.collection.immutable.HashSet[String]("foo", "bar", "baz", "boom")

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[scala.collection.immutable.HashSet[String]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[scala.collection.immutable.HashSet[String]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize immutable TreeSet[String] successfully" in {
    val tm = scala.collection.immutable.TreeSet[String]("foo", "bar", "baz", "boom")

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[scala.collection.immutable.TreeSet[String]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[scala.collection.immutable.TreeSet[String]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize mutable HashSet[String] successfully" in {
    val tm = scala.collection.mutable.HashSet[String]("foo", "bar", "baz", "boom")

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[scala.collection.mutable.HashSet[String]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[scala.collection.mutable.HashSet[String]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }

  it should "serialize and deserialize mutable TreeSet[String] successfully" in {
    val tm = scala.collection.mutable.TreeSet[String]("foo", "bar", "baz", "boom")

    serialization.findSerializerFor(tm) shouldBe a[PekkoKryoSerializer]

    val serialized = serialization.serialize(tm)
    serialized shouldBe a[util.Success[_]]

    val deserialized = serialization.deserialize(serialized.get, classOf[scala.collection.mutable.TreeSet[String]])
    deserialized shouldBe util.Success(tm)

    val bufferSerializer = serialization.findSerializerFor(tm).asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)

    val bufferSerialized = Try(bufferSerializer.toBinary(tm, bb))
    bufferSerialized shouldBe a[util.Success[_]]

    bb.flip()

    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, classOf[scala.collection.mutable.TreeSet[String]].getClass.getName))
    bufferDeserialized shouldBe util.Success(tm)
  }
}
