package stores

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import persistence.Snapshots.Print
import stores.SimplePersistentActor.Snap

import scala.concurrent.duration.DurationInt

object LocalStores extends App {
  val localStoreGuardian = ActorSystem("local-store-guardian", ConfigFactory.load().getConfig("localStores"))
  val spac = localStoreGuardian.actorOf(SimplePersistentActor.props("spac01"))
  val scheduler = localStoreGuardian.scheduler
  implicit val exeCtx = localStoreGuardian.dispatcher

  for (i <- 1 to 10) spac ! s"sending message $i"

  spac ! Print
  spac ! Snap

  for (i <- 11 to 20) spac ! s"sending message [$i]"

  scheduler.scheduleOnce(6 seconds) {
    localStoreGuardian.terminate()
  }
}
