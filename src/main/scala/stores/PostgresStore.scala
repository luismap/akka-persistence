package stores

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

object PostgresStore extends App {
  val postgresStoreGuardian = ActorSystem("local-store-guardian", ConfigFactory.load().getConfig("postgresStore"))
  val postgresSpac = postgresStoreGuardian.actorOf(SimplePersistentActor.props("postgres-spac"))
  val scheduler = postgresStoreGuardian.scheduler
  implicit val exeCtx = postgresStoreGuardian.dispatcher

  for (i <- 1 to 10) postgresSpac ! s"sending message $i"

  import stores.SimplePersistentActor._
  postgresSpac ! Print
  postgresSpac ! Snap

  for (i <- 11 to 20) postgresSpac ! s"sending message [$i]"

  scheduler.scheduleOnce(6 seconds) {
    postgresStoreGuardian.terminate()
  }

}
