package io.altoo.serialization.kryo.pekko

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.{ByteBufferSerializer, SerializationExtension}
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.ByteBuffer
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ParallelActorSystemSerializationTest {
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
       |      "io.altoo.serialization.kryo.pekko.Sample" = kryo
       |    }
       |  }
       |  jvm-exit-on-fatal-error = false
       |}
       |
       |pekko-kryo-serialization {
       |  use-unsafe = false
       |  trace = true
       |  id-strategy = "automatic"
       |  implicit-registration-logging = true
       |  post-serialization-transformations = off
       |}
       |""".stripMargin
}

final case class Sample(value: Option[String]) {
  override def toString: String = s"Sample()"
}
object Sample {
  def apply(value: String) = new Sample(Some(value))
}

class ParallelActorSystemSerializationTest extends AnyFlatSpec with Matchers with Inside {

  private val config = ConfigFactory.parseString(ParallelActorSystemSerializationTest.config)
  private val system1 = ActorSystem("sys1", config)
  private val system2 = ActorSystem("sys2", config)

  // regression test against https://github.com/altoo-ag/pekko-kryo-serialization/issues/237
  it should "be able to serialize/deserialize in highly concurrent load" in {
    val testClass = Sample("auth-store-syncer")

    val results: List[Future[Unit]] = (for (sys <- List(system1, system2))
      yield List(
        Future(testSerialization(testClass, sys))(sys.dispatcher),
        Future(testSerialization(testClass, sys))(sys.dispatcher),
        Future(testSerialization(testClass, sys))(sys.dispatcher),
        Future(testSerialization(testClass, sys))(sys.dispatcher),
        Future(testSerialization(testClass, sys))(sys.dispatcher),
        Future(testSerialization(testClass, sys))(sys.dispatcher))).flatten

    import system1.dispatcher

    import scala.concurrent.duration.*
    Await.result(Future.sequence(results), 10.seconds)
  }

  private def testSerialization(testClass: Sample, sys: ActorSystem): Unit = {
    // find the Serializer for it
    val serializer = SerializationExtension(sys).findSerializerFor(testClass)
    println(sys.settings.name + " " + serializer)
    serializer.getClass.equals(classOf[PekkoKryoSerializer]) shouldBe true
    val serialized = SerializationExtension(sys).serialize(testClass)
    serialized shouldBe a[Success[?]]

    // check serialization/deserialization
    val deserialized = SerializationExtension(sys).deserialize(serialized.get, testClass.getClass)
    inside(deserialized) {
      case util.Success(v) => v shouldBe testClass
    }

    // check buffer serialization/deserialization
    serializer shouldBe a[ByteBufferSerializer]
    val bufferSerializer = serializer.asInstanceOf[ByteBufferSerializer]
    val bb = ByteBuffer.allocate(serialized.get.length * 2)
    bufferSerializer.toBinary(testClass, bb)
    bb.flip()
    val bufferDeserialized = Try(bufferSerializer.fromBinary(bb, testClass.getClass.getName))
    inside(bufferDeserialized) {
      case util.Success(v) => v shouldBe testClass
    }
  }
}
