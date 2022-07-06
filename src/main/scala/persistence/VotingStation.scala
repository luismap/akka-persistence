package persistence

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.util.Random

/**
 * scenario
 * - persistence actor for voting station
 * x. keep status
 * o. citizens who voted
 * o. poll: mapping of candidate -> total votes
 * - actor must recover state if shutdown or restarted
 */
object VotingStation extends App {


  sealed trait VotingCmd

  case class RegisterVote(voter: String, candidate: String) extends VotingCmd

  case object Shutdown extends VotingCmd

  case object Poll extends VotingCmd

  sealed trait VotingData

  case class RecordVote(id: UUID, voter: String, candidate: String) extends VotingData

  object VotingStationActor {
    def props(id: String): Props =
      Props(new VotingStationActor(id))
  }

  class VotingStationActor(id: String) extends PersistentActor with ActorLogging {
    private val voted: ListBuffer[String] = ListBuffer.empty
    private var poll: Map[String, Int] = Map.empty

    override def receiveRecover: Receive = {
      case e@RecordVote(_, voter, candidate) =>
        voted += voter
        val candidateVotes = poll.getOrElse(candidate, 0)
        poll += (candidate -> (candidateVotes + 1))
        log.info(s"[recovered] recovering event $e")
    }

    override def receiveCommand: Receive = {
      case Poll =>
        log.info(s"[polling] current poll: \n${poll.toList.mkString("\n")}")
        log.info(s"[polling] list of candidates: \n${voted}")
      case RegisterVote(voter, candidate) =>
        val event = RecordVote(UUID.randomUUID(), voter, candidate)
        log.info(s"[registering] $event")
        persist(event) {
          e =>
            voted += voter
            val candidateVotes = poll.getOrElse(candidate, 0)
            poll += (candidate -> (candidateVotes + 1))
            log.info(s"[recorded] $e")
        }
      case Shutdown =>
        context.stop(self)
    }

    override def persistenceId: String = id
  }

  val names = List("luis", "anna", "sebas")
  val candidate = List("Abra","Linc", "Kenn")
  val rnd = Random

  val guardian = ActorSystem("guardian")
  val votingStation = guardian.actorOf(VotingStationActor.props("station01"), "station01")
  val scheduler = guardian.scheduler
  implicit val execCtx = guardian.dispatcher

  for (_ <- 0 to 12) votingStation ! RegisterVote(names(rnd.nextInt(3)), candidate(rnd.nextInt(3)))
  votingStation ! Poll

  for (_ <- 0 to 6) votingStation ! RegisterVote(names(rnd.nextInt(3)), candidate(rnd.nextInt(3)))

  votingStation ! Poll

  scheduler.scheduleOnce(6 seconds) {
    guardian.terminate()
  }


}
