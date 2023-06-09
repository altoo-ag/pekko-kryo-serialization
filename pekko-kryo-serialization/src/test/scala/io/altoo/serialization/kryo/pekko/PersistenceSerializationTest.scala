package io.altoo.serialization.kryo.pekko

import java.io.File
import org.apache.pekko.actor.*
import org.apache.pekko.persistence.*
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.*
import scala.language.postfixOps

object PersistenceSerializationTest {
  case object TakeSnapshot
  case object GetState
  case object Boom
  case object SnapshotSaveSuccess
  case object SnapshotSaveFail

  case class Person(fName: String, lName: String)
  case class ExampleState(received: List[Person] = Nil) {
    def updated(s: Person): ExampleState = copy(s :: received)
    override def toString: String = received.reverse.toString
  }

  class SnapshotTestPersistentActor(name: String, probe: ActorRef) extends PersistentActor {
    def persistenceId: String = name

    private var state = ExampleState()

    def receiveCommand: Receive = {
      case TakeSnapshot              => saveSnapshot(state)
      case SaveSnapshotSuccess(_)    => probe ! SnapshotSaveSuccess
      case SaveSnapshotFailure(_, _) => probe ! SnapshotSaveFail
      case s: Person                 => persist(s) { evt => state = state.updated(evt) }
      case GetState                  => sender() ! state.received.reverse
      case Boom                      => throw new Exception("Intentionally throwing exception to test persistence by restarting the actor")
    }

    def receiveRecover: Receive = {
      case SnapshotOffer(_, s: ExampleState) => state = s
      case evt: Person                       => state = state.updated(evt)
    }
  }

  private def config(testNum: Int) =
    s"""
       |pekko {
       |  actor {
       |    serializers {
       |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
       |    }
       |    serialization-bindings {
       |      "scala.collection.immutable.$$colon$$colon" = kryo
       |      "scala.collection.immutable.List" = kryo
       |      "io.altoo.serialization.kryo.pekko.PersistenceSerializationTest$$Person" = kryo
       |      "org.apache.pekko.persistence.serialization.Snapshot" = kryo
       |      "org.apache.pekko.persistence.SnapshotMetadata" = kryo
       |    }
       |  }
       |
       |  persistence {
       |    journal.plugin = "pekko.persistence.journal.inmem"
       |    snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
       |    snapshot-store.local.dir = "target/test-snapshots-$testNum"
       |  }
       |}
       |
       |pekko-kryo-serialization {
       |  type = "nograph"
       |  id-strategy = "incremental"
       |  kryo-reference-map = false
       |  buffer-size = 65536
       |  post-serialization-transformations = "lz4,aes"
       |  encryption {
       |    aes {
       |      key-provider = "io.altoo.serialization.kryo.scala.DefaultKeyProvider"
       |      mode = "AES/GCM/NoPadding"
       |      iv-length = 12
       |      password = "j68KkRjq21ykRGAQ"
       |      salt = "pepper"
       |    }
       |  }
       |  implicit-registration-logging = true
       |}
       |""".stripMargin
}

class PersistenceSerializationTest extends TestKit(ActorSystem(s"testSystem", ConfigFactory.parseString(PersistenceSerializationTest.config(UUID.randomUUID().hashCode()))))
    with AnyWordSpecLike with Matchers with Inside
    with ImplicitSender with BeforeAndAfterAll {
  import PersistenceSerializationTest.*

  private val config = system.settings.config
  private val storageLocations = List("pekko.persistence.snapshot-store.local.dir").map(s => new File(config.getString(s)))
  private val persistentActor = system.actorOf(Props(new SnapshotTestPersistentActor("PersistentActor", testActor)))

  override def beforeAll(): Unit = {
    storageLocations.foreach(FileUtils.deleteDirectory)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = shutdown(system)

  "A persistent actor which is persisted" should {

    "get right serializer" in {
      val serialization = SerializationExtension(system)
      val sample = List(Person("John", "Doe"), Person("Bruce", "Wayne"), Person("Tony", "Stark"))
      val sampleHead = sample.head
      serialization.findSerializerFor(sample) shouldBe a[PekkoKryoSerializer]
      serialization.findSerializerFor(sampleHead) shouldBe a[PekkoKryoSerializer]

      val serialized = serialization.serialize(sample)
      serialized shouldBe a[util.Success[?]]

      val deserialized = serialization.deserialize(serialized.get, classOf[List[Person]])
      deserialized shouldBe util.Success(sample)
    }

    "recover state only from its own correct snapshot file after restart" in {
      persistentActor ! Person("John", "Doe")
      expectNoMessage()
      persistentActor ! Person("Bruce", "Wayne")
      expectNoMessage()
      persistentActor ! TakeSnapshot
      expectMsg(SnapshotSaveSuccess)
      persistentActor ! Person("Tony", "Stark")
      expectNoMessage()
      persistentActor ! Boom
      persistentActor ! GetState
      expectMsg(List(Person("John", "Doe"), Person("Bruce", "Wayne"), Person("Tony", "Stark")))
    }

    "recover correct state after explicitly killing the actor and starting it again" in {
      persistentActor ! Kill // default supervision stops the actor on ActorKilledException

      val newPersistentActor = system.actorOf(Props(new SnapshotTestPersistentActor("PersistentActor", testActor)))
      within(3 seconds) {
        newPersistentActor ! GetState
        expectMsg(List(Person("John", "Doe"), Person("Bruce", "Wayne"), Person("Tony", "Stark")))
      }
    }
  }
}
