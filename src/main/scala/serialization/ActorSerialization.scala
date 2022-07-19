package serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import serialization.UserRegistrationActor.RegisterUser

import java.util.UUID
import scala.concurrent.duration.DurationInt



object ActorSerialization extends App {

  /**
   * for actor serialization
   *  - create serializer
   *  - bind serializer in config for which events it will be use
   */

  val guardian = ActorSystem("Registration", ConfigFactory.load().getConfig("customSerializer"))
  val ura = guardian.actorOf(Props(new UserRegistrationActor("ura-02")))
  val scheduler = guardian.scheduler
  implicit val execCtx = guardian.dispatcher


  for (i <- 0 to 10 ) ura ! RegisterUser(s"name:$i",s"${UUID.randomUUID()}@google.com")


  scheduler.scheduleOnce(6 seconds) {
    guardian.terminate()
  }

}
