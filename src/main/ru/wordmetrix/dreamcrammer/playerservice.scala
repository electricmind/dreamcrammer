package ru.wordmetrix.dreamcrammer

import scala.annotation.tailrec
import scala.util.Random
import java.io._

import android.content.Intent
import android.media.{MediaPlayer, AudioManager}
import android.app.{IntentService, Service}
import android.widget.Toast
import android.os.{Binder, IBinder, AsyncTask, Handler, HandlerThread, Process, Message}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

class PlayerBinder(val service : PlayerService) extends Binder {
    def getService() : PlayerService = {
        return service
    }
}

class PlayerService extends Service with PlayerBase//IntentService("DictLearnService")
{
  var stop : Boolean = true
  var suspended : Boolean = false
  var lock : AnyRef = new Object
  var callbacks = List[Int => Unit]()

  val binder : IBinder  = new PlayerBinder(this)
  val handler : Handler = new Handler();

  val history = new java.util.ArrayList[Int]()
  var history_size = 0

  var servicehandler : Option[Handler] = None

  override
  def onBind(intent : Intent) : IBinder = binder

  def status() : (Boolean, Boolean) = (stop, suspended)

  def pause() : Unit = {
      suspended = true    
  }

  def resume() : Unit = {
      suspended = false
  }

  def exit() : Unit = {
      stop = true
  }

  def seen(callback : Int => Unit) : Unit = {
      callbacks = callback :: callbacks
      println("callbacks :: " + callbacks)
  }

  lazy val db : DB = new SQLiteAndroid(this, "taylor.db", true)
  //lazy val convertors =  new Convertors()(db)
  //import convertors._

  override
  def onCreate() : Unit = {
    servicehandler = Some(new Handler (new HandlerThread("ServiceStartArguments",  Process.THREAD_PRIORITY_BACKGROUND) { start() }.getLooper()) {

        @tailrec
        def scan(words : List[Word])(f : Word => Word) : List[Word] = if (stop) words else if (suspended) { Thread.sleep(1); scan(words)(f) } else words match {
          case x :: xs => scan( xs :+ f(x))(f)
          case List() => List()
        }

        @tailrec
        def scanhmm2(word1 : Word, word2 : Word)(f : Word => Word) : List[Word] = if (stop) List() else if (suspended) { Thread.sleep(1); scanhmm2(word1,word2)(f) } else {
            scanhmm2(word2, {
                val word : Word = hmm(word1, word2) orElse hmm(word2) orElse hmm() getOrElse (new Word(1)(db))
                log("word : %s",word)
                word.track match {
                    case NoData | NeverData => word
                    case SomeData(x) => f(word)
                }
            })(f)
        }


        object hmm {
            def query(q1: String, q2 : String, w1 : Int, w2 : Int) : Option[Word] = {
                db.query(q1, x => x.columnInt(0), w1, w2) headOption match {
                     case Some(x) => db.query(q2, x => new Word(x.columnInt(0))(db), x, w1, w2) match {
                         case Stream() => None
                         case xs => Some(xs(scala.util.Random.nextInt(xs.size)))
                     }
                     case None => None
                }
            }

            def apply()  = query("select max(word_frequency) from word", "select word_id,word_frequency from word where word_frequency > abs(random()) % ?", 0, 1)

            def apply(w1 : Word) = query("select max(hmm1_frequency) from hmm1 where hmm1_word1_id = ?","select hmm1_emit_id,hmm1_frequency from hmm1 where hmm1_frequency > abs(random()) % ? and hmm1_word1_id=?",w1.id,1)

            def apply(w1 : Word, w2 : Word) = query("select max(hmm2_frequency) from hmm2 where hmm2_word1_id = 3 and hmm2_word2_id=4", "select hmm2_emit_id,hmm2_frequency from hmm2 where hmm2_frequency > abs(random()) % 2 and hmm2_word1_id=3 and hmm2_word2_id=4 order by hmm2_frequency", w1.id, w2.id)
        } 


        override
        def handleMessage(msg : Message) : Unit = {
            stop = false
            suspended = false

            lock.synchronized {
                //val (x1 :: x2 :: _) = msg.obj.asInstanceOf[List[Word]]
                //scanhmm2(x1,x2)

                scan(scala.util.Random.shuffle(msg.obj.asInstanceOf[List[Word]]))(word => {
                    handler.post(new Runnable {
                        def run() : Unit = callbacks.map(_(word.id))
                    })

                    handler.post( new Runnable {
                        def run() : Unit =  Toast.makeText(PlayerService.this, "Player: %s".format(word.value), Toast.LENGTH_SHORT).show();
                    })

                    history.add(word.id)
                    history_size = history_size + 1

                    Thread.sleep(500)
                    play(word)
                    Thread.sleep(2000) 
                    play(word)
                    word
                })
            }

            stopSelf(msg.arg1)
        }
    })
  }

  override
  def onStartCommand(intent : Intent, flags : Int, startId : Int) : Int = {
      if (stop) {
          Toast.makeText(this, "Service has started", Toast.LENGTH_SHORT).show()
          servicehandler match {
              case None => {}
              case Some(servicehandler) => {
                  val msg : Message = servicehandler.obtainMessage();
                  msg.arg1 = startId
                  // ticker: application failed in this place with null-exception
                  msg.obj = intent.getIntArrayExtra("word_ids").toList.map(new Word(_)(db))
                  servicehandler.sendMessage(msg)
              }
           }      
      } else {
          Toast.makeText(this, "Pronounciation has already started playing, new intention ignored", Toast.LENGTH_SHORT).show()
      }
      Service.START_STICKY
  }

  override
  def onDestroy() : Unit = {
    Toast.makeText(this, "Service has done", Toast.LENGTH_SHORT).show(); 
  }
}
