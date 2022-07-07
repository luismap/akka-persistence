package persistence

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import jdk.internal.platform.Container

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Random

object Snapshots extends App {

  trait ChatCmd

  case class ReceiveMessage(content: String) extends ChatCmd

  case class SendMessage(content: String) extends ChatCmd

  case object Print extends ChatCmd

  trait ChatEvent

  case class ReceivedMessageRecorded(id: UUID, content: String) extends ChatEvent

  case class SendMessageRecorded(id: UUID, content: String) extends ChatEvent

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10
    var msgToBeCheckpointed = 0
    var currentMsgId = UUID.randomUUID()
    val lastMessages = mutable.Queue.empty[(String, String)]

    override def receiveRecover: Receive = {
      case e@ReceivedMessageRecorded(id, content) =>
        handleCash(contact, content)
        log.info(s"[recovering] $e")
        currentMsgId = id
      case smr@SendMessageRecorded(id, content) =>
        handleCash(owner, content)
        log.info(s"[recovering] $smr")
        currentMsgId = id
      case SnapshotOffer(metadata, content) =>
        log.info(s"[recovered snapshot]")
        content.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    override def receiveCommand: Receive = {
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"[saving-snapshot] successful metada: $metadata  ")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"[saving-snapshot-fail] metada: $metadata, reason: $reason")
      case Print =>
        log.info(s"[printing] ${lastMessages.toList.foreach(println)}")
      case ReceiveMessage(content) =>
        persist(ReceivedMessageRecorded(currentMsgId, content)) {
          e =>
            log.info(s"[recorded-message] event $e")
            handleCash(contact, content)
            currentMsgId = UUID.randomUUID()
            maybeCheckpoint()
        }
      case SendMessage(content) => {
        persist(SendMessageRecorded(currentMsgId, content)) {
          e =>
            log.info(s"[recorded-message] event $e")
            handleCash(owner, content)
            currentMsgId = UUID.randomUUID()
            maybeCheckpoint()
        }
      }
    }

    def maybeCheckpoint(): Unit = {
      msgToBeCheckpointed += 1
      if (msgToBeCheckpointed >= MAX_MESSAGES) {
        log.info(s"[saving-checkpoint]")
        saveSnapshot(lastMessages) //needs to be serializable, async
        msgToBeCheckpointed = 0
      }

    }

    def handleCash(sender: String, content: String) = {
      if (lastMessages.size >= MAX_MESSAGES) lastMessages.dequeue()
      lastMessages.enqueue((sender, content))
    }

    override def persistenceId: String = s"$owner-$contact-chat"
  }

  val sysGuardian = ActorSystem("sysGuardian")
  val chat01 = sysGuardian.actorOf(Chat.props("luis", "ann"))
  val scheduler = sysGuardian.scheduler
  implicit val execCtx = sysGuardian.dispatcher
  val rnd = Random
  val greets = List("hi", "hello", "tja")
  val words = List("apple", "car", "grass")

  for (e <- 1 to 100) {
    val message1 = greets(rnd.nextInt(3)) + " " + words(rnd.nextInt(3)) + " " + e
    if (e % 2 == 0) chat01 ! SendMessage(message1)
    else chat01 ! ReceiveMessage(message1)
  }

  chat01 ! Print
  /**
   * event1
   * event2
   * snapshot //akka will recover from last snapshot and the following events
   * event3
   * event4
   */

  /**
   * how to use snapshots.
   * - after each persist, maybe snapshot
   * - handle SnapshotOffer msg in recover
   * - handle Savesnapshot[Success/Failure] msg
   */
  scheduler.scheduleOnce(6 seconds) {
    sysGuardian.terminate()
  }
}
