package io.altoo.serialization.kryo.pekko.performance

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.{Serialization, SerializationExtension}
import org.apache.pekko.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration.*

object KryoSerializationBenchmark {

  // -----------------------------
  // Test messages (vary complexity)
  // -----------------------------
  case class SmallMessage(id: Int, name: String)
  case class CollectionsMessage(id: Int, tags: List[String], tagsSet: Set[String], tagsMap: Map[Int, String])
  case class Nested(name: String, firstName: String, age: Int, tags: Set[String])
  case class NestedMessage(id: Int, inner: Nested)
  case class LargeMessageArray(id: Int, payload: Array[Byte])
  case class LargeMessageByteString(id: Int, payload: ByteString)

  case class BenchmarkResult(
                              serializerName: String,
                              messageName: String,
                              avgTimeMs: Double,
                              throughputOpsPerSec: Double,
                              totalOperations: Long,
                            )

  case class RunStats(durationNanos: Long, operations: Long)

  // -----------------------------
  // Benchmark configuration
  // -----------------------------
  val TargetRunDuration = 5.seconds
  val BenchmarkIterations = 3
  val MinWarmupIterations = 3
  val WarmupStableThreshold = 0.2 // 20%

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
    println(s"Target run duration: $TargetRunDuration\n")

    val resultsJava = runBenchmarkWithSettings("java", configWithJavaSerialization)
    val resultsKryo = runBenchmarkWithSettings("kryo", configWithKryo)

    println("\n=== Benchmark Summary ===")
    println(f"${"Serializer"}%-20s ${"MessageType"}%-30s ${"Throughput(ops/sec)"}%-20s")

    (resultsJava ++ resultsKryo).foreach { r =>
      println(f"${r.serializerName}%-20s ${r.messageName}%-30s ${formatDouble(r.throughputOpsPerSec)}%20s")
    }
  }

  private def runBenchmarkWithSettings(name: String, config: Config): Seq[BenchmarkResult] = {
    val fullConfig = config.withFallback(ConfigFactory.load())
    implicit val system: ActorSystem = ActorSystem("benchmark", fullConfig)
    val serialization = SerializationExtension(system)

    // -----------------------------
    // Pre-generate messages
    // -----------------------------
    val messagesPerBatch = 10_000

    val smallMessages = Array.fill(messagesPerBatch)(SmallMessage(1, "test"))

    val collectionsMessages = Array.fill(messagesPerBatch)(
      CollectionsMessage(
        id = 1,
        tags = List.tabulate(500)(i => s"tag$i"),
        tagsSet = (0 until 500).map(i => s"set$i").toSet,
        tagsMap = (0 until 500).map(i => i -> s"value$i").toMap,
      )
    )

    val nestedMessages = {
      val inner = Nested(
        name = "Doe",
        firstName = "John",
        age = 42,
        tags = (0 until 2000).map(i => s"nested$i").toSet,
      )
      Array.fill(messagesPerBatch)(NestedMessage(1, inner))
    }

    val largeArrayMessages =
      Array.fill(messagesPerBatch)(LargeMessageArray(1, Array.fill(10000)(1.toByte)))

    val largeByteStringMessages =
      Array.fill(messagesPerBatch)(
        LargeMessageByteString(1, ByteString.fromArrayUnsafe(Array.fill(10000)(1.toByte)))
      )

    val results = Seq(
      runBenchmark(name, "SmallMessage", smallMessages, serialization),
      runBenchmark(name, "CollectionsMessage", collectionsMessages, serialization),
      runBenchmark(name, "NestedMessage", nestedMessages, serialization),
      runBenchmark(name, "LargeMessageByte[]", largeArrayMessages, serialization),
      runBenchmark(name, "LargeMessageByteString", largeByteStringMessages, serialization),
    )

    Await.result(system.terminate(), 10.seconds)
    results
  }

  def runBenchmark[T <: AnyRef](
                                 serializerName: String,
                                 messageName: String,
                                 messages: Array[T],
                                 serialization: Serialization,
                               ): BenchmarkResult = {

    println(s"\n=== $serializerName - $messageName ===")

    // -----------------------------
    // Warmup (adaptive)
    // -----------------------------
    var warmupTimes = List.empty[Long]
    var continueWarmup = true

    while (continueWarmup) {
      val stats = runOnce(messages, serialization, warmup = true)
      warmupTimes = warmupTimes :+ stats.durationNanos

      if (warmupTimes.size >= MinWarmupIterations) {
        val last = warmupTimes.last
        val prev = warmupTimes.init.last
        val diff = math.abs(last - prev).toDouble / prev
        continueWarmup = diff > WarmupStableThreshold
      }
    }

    // -----------------------------
    // Measured runs
    // -----------------------------
    val runs = (1 to BenchmarkIterations).map { _ =>
      runOnce(messages, serialization, warmup = false)
    }

    val totalOps = runs.map(_.operations).sum
    val totalTimeNanos = runs.map(_.durationNanos).sum

    val avgTimeMs = (totalTimeNanos.toDouble / runs.size) / 1e6
    val throughputOpsPerSec = totalOps.toDouble / (totalTimeNanos.toDouble / 1e9)

    println(f"Throughput: $throughputOpsPerSec%.0f ops/sec")

    BenchmarkResult(serializerName, messageName, avgTimeMs, throughputOpsPerSec, totalOps)
  }

  def runOnce[T <: AnyRef](
                            messages: Array[T],
                            serialization: Serialization,
                            warmup: Boolean,
                          ): RunStats = {

    val start = System.nanoTime()
    val deadline = start + TargetRunDuration.toNanos

    var i = 0
    var ops = 0L
    val len = messages.length

    while (System.nanoTime() < deadline) {
      val msg = messages(i)
      val serializer = serialization.serializerFor(msg.getClass)

      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes)

      i += 1
      ops += 1

      if (i == len) i = 0
    }

    val end = System.nanoTime()
    val duration = end - start

    if (!warmup) {
      val seconds = duration / 1e9
      val throughput = ops / seconds
      println(f"Run took: ${duration / 1e6}%.2f ms | ops: $ops | throughput: ${formatDouble(throughput)} ops/sec")
    }

    RunStats(duration, ops)
  }

  private def formatDouble(n: Double): String = {    f"$n%,.0f".replace(',', '\'')  }
}