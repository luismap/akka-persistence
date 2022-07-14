package stores

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

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