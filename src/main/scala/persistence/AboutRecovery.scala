package persistence

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}
import akka.remote.transport.ThrottlerTransportAdapter.Direction.Receive

import scala.concurrent.duration.DurationInt

object AboutRecovery extends App {

  case class MyCommand(cmd: String)

  case class MyEvent(event: String)
  case class MyStatelessEvent(id: Int, event: String)

  class RecoveryActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case MyEvent(content) =>
        if (content.contains("459")) throw new RuntimeException("Failing at 459")
        log.info(s"[recovered] data $content")
      case RecoveryCompleted =>
        log.info(s"[recovery] recovery completed")
    }

    override def receiveCommand: Receive = {
      case MyCommand(content) =>
        persist(MyEvent(content)) {
          e =>
            log.info(s"[persisted] data $e")
        }
    }


    /**
     * if recovery failure, actor state is inconsistence
     * then the actor will stop
     *
     * @param cause
     * @param event
     */
    override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("failed to recover")
      super.onRecoveryFailure(cause, event)
    }


    /**
     * if you want, you can customize recovery so that
     * - the corrupt part will not be reach
     * - snapshots
     * ect
     * but, do not use the actor anymore
     * @return
     */
    override def recovery: Recovery = Recovery(toSequenceNr = 400)
    //override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    //override def recovery: Recovery = Recovery.none


    override def persistenceId: String = "recoveryActor01"
  }


  class StatelessRecoveryActor extends RecoveryActor {
    override def receiveRecover: Receive = {
      case MyStatelessEvent(id,content) =>
        log.info(s"[recovered] event id: $id and contenct $content")
        context.become(online(id + 1)) //useful to restart after the las event
    }


    override def receiveCommand: Receive = online(0)

    override def persistenceId: String = "statelesssRecoveryActor"

    def online(id: Int): Receive = {
      case MyCommand(content) =>
        persist(MyStatelessEvent(id,content)){
          e =>
            log.info(s"[persisted] stateless event $e")
            context.become(online(id + 1))

        }
    }
  }

  val sysGuardian = ActorSystem("sysGuardian")
  val recoveryActor = sysGuardian.actorOf(Props[RecoveryActor], "recovery-actor")
  val statelessRecoveryActor = sysGuardian.actorOf(Props[StatelessRecoveryActor], "stateless-recovery-actor")
  val scheduler = sysGuardian.scheduler
  implicit val ctx = sysGuardian.dispatcher

  //for(n <- 0 to 100) recoveryActor ! MyCommand(s"data number $n")
  //message are stashed until recovery has happened

  for(n <- 0 to 100) statelessRecoveryActor ! MyCommand(s"data number $n")

  scheduler.scheduleOnce(6 seconds) {
    sysGuardian.terminate()
  }


}
