package stores

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory
import stores.LocalStores.SimplePersistentActor.{Print, Snap}

import scala.concurrent.duration.DurationInt

object LocalStores extends App {

  object SimplePersistentActor {
    trait SPACmd

    case object Print extends SPACmd

    case object Snap extends SPACmd

    trait SPACEvent
    case class MessageRecorded(message: Any) extends SPACEvent


    def props(id: String): Props = Props(new SimplePersistentActor(id))
  }

  class SimplePersistentActor(id: String) extends PersistentActor with ActorLogging {

    import SimplePersistentActor._

    var processedMessages = 0

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info(s"[recovery] completed")
      case SnapshotOffer(metadata, processedMsg: Int) =>
        log.info(s"[snapshot-recover] metadata: $metadata content: $processedMsg")
        processedMessages = processedMsg
      case MessageRecorded(message) =>
        log.info(s"[recovered] message $message")
        processedMessages += 1
    }

    override def receiveCommand: Receive = {
      case Print =>
        log.info(s"[printing] persisted messages: $processedMessages")
      case Snap =>
        saveSnapshot(processedMessages)
      case SaveSnapshotSuccess(metadata) => log.info(s"[snapshot] saving successful $metadata")
      case SaveSnapshotFailure(metadata, cause) =>
        log.error(s"[snapshot-failure] metadata: $metadata cause: $cause")
      case message =>
        persist(MessageRecorded(message)) {
          e =>
            log.info(s"[persisted] $e")
            processedMessages += 1
        }
    }

    override def persistenceId: String = id
  }

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
