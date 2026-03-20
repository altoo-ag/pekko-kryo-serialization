package io.altoo.serialization.kryo.pekko.performance

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.reflect.ClassTag

object KryoSerializationBenchmark {

  // -----------------------------
  // Test messages (vary complexity)
  // -----------------------------
  case class SmallMessage(id: Int, name: String)
  case class MediumMessage(id: Int, data: Array[Byte], tags: List[String])
  case class NestedMessage(id: Int, inner: SmallMessage, values: Vector[Double])
  case class LargeMessageAR(id: Int, payload: Array[Byte])
  case class LargeMessageBS(id: Int, payload: ByteString)

  // -----------------------------
  // Run result case class
  // -----------------------------
  case class BenchmarkResult(
                              serializerName: String,
                              messageName: String,
                              avgTimeMs: Double,
                              throughputOpsPerSec: Double,
                              messagesPerRun: Int
                            )

  // -----------------------------
  // Config
  // -----------------------------
  val WarmupIterations = 3
  val BenchmarkIterations = 5
  val MessagesPerRun = 100_000

  private val configWithKryo = ConfigFactory.parseString(
    """
      pekko {
        actor {
          serializers {
            kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
          }
          serialization-bindings {
            "scala.Product" = kryo
          }
        }
      }
    """
  )

  private val configWithJavaSerialization = ConfigFactory.parseString(
    """
      pekko {
        actor {
          allow-java-serialization = on
          warn-about-java-serializer-usage = off
        }
      }
    """
  )

  def main(args: Array[String]): Unit = {
    println("Starting benchmark...")
    println(s"Messages per run: $MessagesPerRun\n")

    val resultsJava = runBenchmarkWithSettings("java", configWithJavaSerialization)
    val resultsKryo = runBenchmarkWithSettings("kryo", configWithKryo)

    // Print table comparing all results
    println("\n=== Benchmark Summary ===")
    println(f"${"Serializer"}%-20s ${"MessageType"}%-30s ${"AvgTime(ms)"}%-15s ${"Throughput(ops/sec)"}%-20s")
    (resultsJava ++ resultsKryo).foreach { r =>
      println(f"${r.serializerName}%-20s ${r.messageName}%-30s ${r.avgTimeMs}%-15.2f ${r.throughputOpsPerSec}%-20.0f")
    }
  }

  private def runBenchmarkWithSettings(name: String, config: Config): Seq[BenchmarkResult] = {
    val fullConfig = config.withFallback(ConfigFactory.load())
    given system: ActorSystem = ActorSystem("benchmark", fullConfig)
    val serialization = SerializationExtension(system)

    val results = Seq(
      runBenchmark(name, "SmallMessage", () => SmallMessage(1, "test"), serialization),
      runBenchmark(name, "MediumMessage", () => MediumMessage(1, Array.fill(256)(1.toByte), List("a","b","c")), serialization),
      runBenchmark(name, "NestedMessage", () => NestedMessage(1, SmallMessage(2,"inner"), Vector.fill(20)(math.random())), serialization),
      runBenchmark(name, "LargeMessageByte[]", () => LargeMessageAR(1, Array.fill(4096)(1.toByte)), serialization),
      runBenchmark(name, "LargeMessageByteString", () => LargeMessageBS(1, ByteString.fromArrayUnsafe(Array.fill(4096)(1.toByte))), serialization)
    )

    Await.result(system.terminate(),10.seconds)
    results
  }

  // -----------------------------
  // Benchmark runner
  // -----------------------------
  def runBenchmark[T <: AnyRef: ClassTag](
                                           serializerName: String, messageName: String,
                                           generator: () => T,
                                           serialization: org.apache.pekko.serialization.Serialization
                                         )(using system: ActorSystem): BenchmarkResult = {

    println(s"\n=== $serializerName - $messageName===")

    // Warmup
    for (_ <- 1 to WarmupIterations) {
      runOnce(generator, serialization, warmup = true)
    }

    // Measured runs
    val times = (1 to BenchmarkIterations).map { _ =>
      runOnce(generator, serialization, warmup = false)
    }

    val avgNanos = times.sum / times.size
    val avgTimeMs = avgNanos / 1e6
    val throughputOpsPerSec = 1_000_000_000.0 / avgNanos * MessagesPerRun

    println(f"Avg time per run: $avgTimeMs%.2f ms")
    println(f"Throughput: $throughputOpsPerSec%.0f ops/sec")

    BenchmarkResult(serializerName,messageName, avgTimeMs, throughputOpsPerSec, MessagesPerRun)
  }

  // -----------------------------
  // Single run
  // -----------------------------
  def runOnce[T <: AnyRef: ClassTag](
                                      generator: () => T,
                                      serialization: org.apache.pekko.serialization.Serialization,
                                      warmup: Boolean
                                    )(using system: ActorSystem): Long = {

    val messages: Array[T] = Array.fill(MessagesPerRun)(generator())

    val start = System.nanoTime()
    var i = 0
    while (i < messages.length) {
      val msg = messages(i)

      val serializer = serialization.serializerFor(msg.getClass)
      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes)

      i += 1
    }
    val end = System.nanoTime()
    val duration = end - start

    if (!warmup) {
      println(f"Run took: ${duration / 1e6}%.2f ms")
    }

    duration
  }
}