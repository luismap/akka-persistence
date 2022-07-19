package serialization

import akka.actor.ActorLogging
import akka.persistence.PersistentActor

object UserRegistrationActor {
  trait UraCmd

  case class RegisterUser(name: String, email: String) extends UraCmd

  trait UraEvents

  case class UserRegistered(id: Int, name: String, email: String) extends UraEvents
}

class UserRegistrationActor(id: String) extends PersistentActor with ActorLogging {

  import UserRegistrationActor._

  override def receiveRecover: Receive = {
    case e@UserRegistered(id, _, _) =>
      log.info(s"[recovered] event $e")
      context.become(onLine(id + 1))
  }

  override def receiveCommand: Receive = onLine(0)

  def onLine(currentId: Int): Receive = {
    case RegisterUser(name, email) =>
      log.info(s"[registering] user $name")
      persist(UserRegistered(currentId, name, email)) {
        e =>
          log.info(s"[registered] user $name")
          context.become(onLine(currentId + 1), true)
      }
  }

  override def persistenceId: String = id
}