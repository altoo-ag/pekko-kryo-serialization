package io.altoo.serialization.kryo.pekko

import org.apache.pekko.serialization.SerializationExtension
import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.pekko.performance.Time
import io.altoo.serialization.kryo.pekko.performance.Time.Time
import io.altoo.serialization.kryo.pekko.testkit.AbstractPekkoTest

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object EnumSerializationTest {
  private val config = {
    """
      |pekko {
      |  actor {
      |    serializers {
      |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
      |    }
      |    serialization-bindings {
      |      "java.io.Serializable" = kryo
      |    }
      |  }
      |}
      |pekko-kryo-serialization {
      |  id-strategy = "default"
      |}
      |""".stripMargin
  }
}

class EnumSerializationTest extends AbstractPekkoTest(ConfigFactory.parseString(EnumSerializationTest.config)) {
  private val serialization = SerializationExtension(system)


  behavior of "Enumeration serialization"

  it should "be threadsafe" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    val listOfTimes = Time.values.toList
    val bytes = serialization.serialize(listOfTimes).get
    val futures = 1 to 2 map (_ => Future[List[Time]] {
      serialization.deserialize(bytes.clone, classOf[List[Time]]).get
    })

    val result = Await.result(Future.sequence(futures), Duration.Inf)

    assert(result.forall { res => res == listOfTimes })
  }
}
