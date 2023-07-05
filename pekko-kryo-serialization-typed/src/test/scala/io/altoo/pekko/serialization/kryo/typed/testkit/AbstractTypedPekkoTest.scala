package io.altoo.serialization.kryo.pekko.typed.testkit

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.Config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AbstractTypedPekkoTest(config: Config) extends AnyFlatSpec with BeforeAndAfterAll with Matchers {
  protected val testKit: ActorTestKit = ActorTestKit("testSystem", config)

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
