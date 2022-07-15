package stores

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

object CassandraStore extends App {

  val casssandraStoreGuardian = ActorSystem("local-store-guardian", ConfigFactory.load().getConfig("cassandraStore"))
  val cassandraSpac = casssandraStoreGuardian.actorOf(SimplePersistentActor.props("cassandra-spac"))
  val scheduler = casssandraStoreGuardian.scheduler
  implicit val exeCtx = casssandraStoreGuardian.dispatcher

  for (i <- 1 to 10) cassandraSpac ! s"sending message $i"

  import stores.SimplePersistentActor._
  cassandraSpac ! Print
  cassandraSpac ! Snap

  for (i <- 11 to 20) cassandraSpac ! s"sending message [$i]"

  scheduler.scheduleOnce(6 seconds) {
    casssandraStoreGuardian.terminate()
  }
}
