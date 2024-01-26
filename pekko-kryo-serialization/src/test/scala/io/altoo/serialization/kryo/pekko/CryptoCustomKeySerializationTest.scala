package io.altoo.serialization.kryo.pekko

import com.esotericsoftware.kryo.kryo5.objenesis.strategy.StdInstantiatorStrategy
import com.esotericsoftware.kryo.kryo5.util.*
import com.typesafe.config.{Config, ConfigFactory}
import io.altoo.serialization.kryo.pekko.serializer.scala.*
import io.altoo.serialization.kryo.pekko.testkit.{AbstractPekkoTest, KryoSerializationTesting}
import io.altoo.serialization.kryo.scala.serializer.ScalaKryo
import io.altoo.serialization.kryo.scala.{DefaultKeyProvider, KryoCryptographer}
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.{ByteBufferSerializer, SerializationExtension}

import java.nio.ByteBuffer
import scala.collection.immutable.HashMap

class KryoCryptoTestKey extends DefaultKeyProvider {
  override def aesKey(config: Config): Array[Byte] = "TheTestSecretKey".getBytes("UTF-8")
}

object CryptoCustomKeySerializationTest {
  private val config = {
    s"""
       |pekko {
       |  actor {
       |    serializers {
       |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
       |    }
       |    serialization-bindings {
       |      "scala.collection.immutable.HashMap" = kryo
       |      "[Lscala.collection.immutable.HashMap;" = kryo
       |      "scala.collection.mutable.LongMap" = kryo
       |      "[Lscala.collection.mutable.LongMap;" = kryo
       |      "${ScalaVersionRegistry.immutableHashMapImpl}" = kryo
       |      "${ScalaVersionRegistry.immutableHashSetImpl}" = kryo
       |    }
       |  }
       |}
       |
       |pekko-kryo-serialization {
       |  post-serialization-transformations = aes
       |  encryption {
       |    aes {
       |      key-provider = "io.altoo.serialization.kryo.pekko.KryoCryptoTestKey"
       |      mode = "AES/GCM/NoPadding"
       |      iv-length = 12
       |    }
       |  }
       |}
       |""".stripMargin
  }
}

class CryptoCustomKeySerializationTest extends AbstractPekkoTest(ConfigFactory.parseString(CryptoCustomKeySerializationTest.config)) with KryoSerializationTesting {
  private val encryptedSerialization = SerializationExtension(system)

  protected val kryo: ScalaKryo = new ScalaKryo(new DefaultClassResolver(), new MapReferenceResolver())
  kryo.setRegistrationRequired(false)
  private val kryoInit = new DefaultKryoInitializer()
  kryoInit.system_=(system.asInstanceOf[ExtendedActorSystem])
  kryoInit.preInit(kryo)
  kryoInit.init(kryo)
  kryoInit.postInit(kryo)
  private val instStrategy = kryo.getInstantiatorStrategy.asInstanceOf[DefaultInstantiatorStrategy]
  instStrategy.setFallbackInstantiatorStrategy(new StdInstantiatorStrategy())
  kryo.setInstantiatorStrategy(instStrategy)

  behavior of "Custom key encrypted serialization"

  it should "encrypt with custom aes key" in {
    val atm = List {
      HashMap[String, Any](
        "foo" -> "foo",
        "bar" -> "foo,bar,baz",
        "baz" -> 124L)
    }.toArray

    val serialized = encryptedSerialization.findSerializerFor(atm).toBinary(atm)
    val decrypted = new KryoCryptographer("TheTestSecretKey".getBytes("UTF-8"), "AES/GCM/NoPadding", 12).fromBinary(serialized)

    val deserialized = deserialize[Array[HashMap[String, Any]]](decrypted)
    atm shouldBe deserialized

    val bb = ByteBuffer.allocate(serialized.length)
    encryptedSerialization.findSerializerFor(atm).asInstanceOf[ByteBufferSerializer].toBinary(atm, bb)
    val bufferDeserialized = deserialize[Array[HashMap[String, Any]]](decrypted)
    atm shouldBe bufferDeserialized
  }

  it should "decrypt with custom aes key" in {
    val atm = List {
      HashMap[String, Any](
        "foo" -> "foo",
        "bar" -> "foo,bar,baz",
        "baz" -> 124L)
    }.toArray

    val serialized = serialize[Array[HashMap[String, Any]]](atm)
    val encrypted = new KryoCryptographer("TheTestSecretKey".getBytes("UTF-8"), "AES/GCM/NoPadding", 12).toBinary(serialized)

    val deserialized = encryptedSerialization.findSerializerFor(atm).fromBinary(encrypted)
    atm shouldBe deserialized

    val bufferDeserialized = encryptedSerialization.findSerializerFor(atm).asInstanceOf[ByteBufferSerializer].fromBinary(ByteBuffer.wrap(encrypted), atm.getClass.getName)
    atm shouldBe bufferDeserialized
  }
}
