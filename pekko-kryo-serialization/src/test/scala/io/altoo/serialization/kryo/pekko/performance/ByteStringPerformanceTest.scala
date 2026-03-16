package io.altoo.serialization.kryo.pekko.performance

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.PekkoKryoSerializer
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.*
import org.apache.pekko.util.ByteString
import org.scalatest.*
import org.scalatest.flatspec.AnyFlatSpec

object ByteStringPerformanceTest {
  def main(args: Array[String]): Unit = {
    (new PerformanceTests).execute()
  }

  private class PerformanceTests extends AnyFlatSpec with BeforeAndAfterAllConfigMap {
    private val defaultConfig = ConfigFactory.parseString(
      """
      pekko {
        actor {
          serializers {
            kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
          }
          serialization-bindings {
            "org.apache.pekko.util.ByteString$ByteString1C" = kryo
            "org.apache.pekko.util.ByteString" = kryo
          }
        }
      }
      pekko-kryo-serialization {
        type = "nograph"
        id-strategy = "incremental"
        post-serialization-transformations = off
      }
  """)

    var iterations: Int = 10000
    var size: Int = 1000000

    override def beforeAll(configMap: ConfigMap): Unit = {
      configMap.getOptional[String]("iterations").foreach { i => iterations = i.toInt }
      configMap.getOptional[String]("size").foreach { s => size = s.toInt }
    }

    def timeIt[A](name: String, loops: Int)(a: => A) = {
      val now = System.nanoTime
      var i = 0
      while (i < loops) {
        a
        i += 1
      }
      val ms = (System.nanoTime - now) / 1000000.0
      println(f"$name%s\t$ms%.1f\tms\t=\t${loops * size / ms / 1024 / 1024}%.2f\tMB/ms")
    }

    "ByteStringSerializer" should "be fast" in {
      val system = ActorSystem("ByteStringPerformance", defaultConfig)
      val serialization = SerializationExtension(system)
      val data = ByteString(Array.fill[Byte](size)(1))

      val serializer = serialization.findSerializerFor(data)
      assert(serializer.isInstanceOf[PekkoKryoSerializer])

      val bytes = serialization.serialize(data).get

      println(s"Warmup...")
      for (_ <- 1 to 1000) {
        serialization.serialize(data)
        serialization.deserialize(bytes, classOf[ByteString])
      }

      timeIt(s"ByteString Serialize ($size bytes):   ", iterations) {
        serialization.serialize(data)
      }
      timeIt(s"ByteString Deserialize ($size bytes): ", iterations) {
        serialization.deserialize(bytes, classOf[ByteString])
      }

      system.terminate()
    }
  }
}
