package patternsAndBestPractices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.apache.log4j.Logger
import patternsAndBestPractices.Genre.Genre

import scala.concurrent.duration.DurationInt
import scala.util.Random

object PersistenceQueryEx extends App {
  val log = Logger.getLogger(this.getClass.getName)

  val persistenceQueryGuardian = ActorSystem("pqGuardian", ConfigFactory.load().getConfig("persistenceQuery"))
  val scheduler = persistenceQueryGuardian.scheduler
  implicit val execCtx = persistenceQueryGuardian.dispatcher
  var n = 0

  //read journal
  val readJournal = PersistenceQuery(persistenceQueryGuardian).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  val ids = readJournal.persistenceIds() //returns a stream
  implicit val materilizer = ActorMaterializer()(persistenceQueryGuardian)

  //by persistence id
  val byIds = readJournal.eventsByPersistenceId("sa-3", 0, Long.MaxValue)
  byIds.runForeach(e => log.info(s"[events] $e"))
  ids.runForeach(e => log.info(s"[id] $e"))

  //read by tags(it will work, just needs more time to read)
  val byTags = readJournal.eventsByTag("POP", Offset.noOffset)
  byTags.runForeach( e => log.info(s"[reading event with TAG] $e"))

  scheduler.schedule(1 seconds, 1 seconds) {
   //exec
  }
  def exec = {
    val ac = persistenceQueryGuardian.actorOf(SimpleActor.props(s"sa-$n"))
    ac ! s"Hi Actor sa-$n"
    n += 1
  }

  val msa = persistenceQueryGuardian.actorOf(Props[MusicStoreCheckoutActor],"msa")
  val rnd = Random
  val genres = Genre.values.toArray
  val maxGenres = genres.length
  scheduler.scheduleOnce(3 seconds) {
    //exec2
  }
  def exec2 = {
    for (e <- 0 to 6) {
      val songs = for (g <- 0 to rnd.nextInt(maxGenres)) yield {
        Song(s"artist $e", s"kings and queens $e",genres(g))
      }
      msa ! BuyPlayList(songs.toList)
    }
  }
  scheduler.scheduleOnce(90 seconds) {
    persistenceQueryGuardian.terminate()
  }
}

object SimpleActor {
  def props(id: String): Props = Props(new SimpleActor(id))
}

class SimpleActor(id: String) extends PersistentActor with ActorLogging {
  override def receiveRecover: Receive = {
    case e => log.info(s"[recovered] $e")
  }

  override def receiveCommand: Receive = {
    case cmd =>
      persist(cmd) {
        e => log.info(s"[persisted] $e")
      }
  }

  override def persistenceId: String = id
}

object Genre extends Enumeration {
  type Genre = Value
  val POP, ROCK, JAZZ, DISCO = Value
}

case class Song(artist: String, title: String, genre: Genre) //data

case class BuyPlayList(songs: List[Song]) //cmd

case class PlayListBought(id: Int, songs: List[Song]) //event

class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
  var latestPlayListId = 0

  override def receiveRecover: Receive = {
    case e@PlayListBought(id, _) =>
      log.info(s"[recovered] playlist $e")
      latestPlayListId = id
  }

  override def receiveCommand: Receive = {
    case BuyPlayList(songs) =>
      persist(PlayListBought(latestPlayListId, songs)) {
        e =>
          log.info(s"[persisted] playlist $e")
          latestPlayListId += 1
      }
  }

  override def persistenceId: String = "music-store-AB"
}

class MusicStoreEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = "MusicStoreManifest"

  override def toJournal(event: Any): Any = event match {
    case event@PlayListBought(id, songs) =>
      val tags = songs.map(_.genre.toString).toSet //only unique tags
      //save event as a tagged event
      Tagged(event, tags)
    case other => other
  }
}