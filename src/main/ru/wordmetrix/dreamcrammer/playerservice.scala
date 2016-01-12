package ru.wordmetrix.dreamcrammer

import android.graphics.{BitmapFactory, Bitmap}
import android.support.v4.app.{NotificationManagerCompat, NotificationCompat}
import android.support.v4.app.NotificationCompat.{BigPictureStyle, WearableExtender}

import scala.annotation.tailrec
import scala.util.{Try, Random}
import java.io._

import android.content.Intent
import android.media.{MediaPlayer, AudioManager}
import android.app.{PendingIntent, IntentService, Service}
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

    val playerPendingIntent: PendingIntent = PendingIntent.getActivity(
      PlayerService.this,
      0,
      new Intent(PlayerService.this, classOf[Player]) {
        //putExtra("word_ids", queryPlayerIds())
      },
      0)

    val notificationBuilder: NotificationCompat.Builder =
      new NotificationCompat.Builder(PlayerService.this)
        .setSmallIcon(R.drawable.play)
        .setContentTitle("Player")
        .setContentText("Start player")
        .setStyle(new NotificationCompat.InboxStyle()
//          .addLine("Alex Faaborg   Check this out")
//          .addLine("Jeff Chang   Launch Party")
//          .setSummaryText("johndoe@gmail.com")
        )
        .setGroup("wordnotification")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setGroupSummary(true)
        //setContentIntent(playerPendingIntent).
        .extend(
          new WearableExtender()
            .addAction(
              new NotificationCompat.Action.Builder(
                 R.drawable.play,
                 s"""Start Player""",
                 playerPendingIntent).build())
            .addPage(
                new NotificationCompat.Builder(PlayerService.this)
                  .setStyle(
                     new NotificationCompat.BigTextStyle()
                        .setBigContentTitle("dasdasd")
                        .bigText("sadsa"))
                        .build()))

    notificationManager.notify(notifyId.next(), notificationBuilder.build())

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
                scan(scala.util.Random.shuffle(msg.obj.asInstanceOf[List[Word]]))(word => {
                    handler.post(new Runnable {
                        def run() : Unit = callbacks.map(_(word.id))
                    })

                    handler.post( new Runnable {
                        def run() : Unit =  Toast.makeText(PlayerService.this, "Player: %s".format(word.value), Toast.LENGTH_SHORT).show();
                    })

                    history.add(word.id)
                    history_size = history_size + 1

                    val vocabularyPendingIntent: PendingIntent = PendingIntent.getActivity(
                      PlayerService.this,
                      word.id,
                      new Intent(PlayerService.this, classOf[Vocabulary]) {
                        putExtra("word_id", word.id)
                      },
                      0)

                    val stopPendingIntent: PendingIntent = PendingIntent.getService(
                      PlayerService.this,
                      0,
                      new Intent(PlayerService.this, classOf[PlayerService]) {
                        putExtra("doStop", true)
                      },
                      0)

                    def bitmap = word.pictures.view.take(0).flatMap(_.bodyOption).flatMap(body => Try(
                      Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(body, 0, body.size), 256, 256, true)).toOption)

                    val notificationBuilder: NotificationCompat.Builder =
                      new NotificationCompat.Builder(PlayerService.this)
                        .setSmallIcon(R.drawable.play)
                        .setContentTitle(word.value)
                        .setContentText(word.ipa getOrElse word.descriptions.mkString("\n"))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setGroup("wordnotification")
                        //setAutoCancel(true).
                        .setContentIntent(vocabularyPendingIntent)
                        //setGroupSummary(true).
                        .extend(
                           bitmap.foldLeft(new WearableExtender()
                             .addAction(
                               new NotificationCompat.Action.Builder(
                                 R.drawable.lookat,
                                 s"""open ${word.value}""",
                                 vocabularyPendingIntent).build())) {
                             case (extender, bitmap) =>
                               extender.addPage(
                                 new NotificationCompat.Builder(PlayerService.this)
                                   .setStyle(
                                     new BigPictureStyle().bigPicture(bitmap))
                                   .extend(new WearableExtender().setHintShowBackgroundOnly(true))
                                   .build())}
                             .addAction(
                               new NotificationCompat.Action.Builder(
                                 R.drawable.exit,
                                 s"""Stop player""",
                                 stopPendingIntent).build())
                             .addPage(
                               new NotificationCompat.Builder(PlayerService.this)
                                 .setStyle(
                                   new NotificationCompat.BigTextStyle()
                                     .setBigContentTitle(word.value)
                                     .bigText(word.descriptions.map(_.value).mkString("\n"))
                                     .bigText(word.phrases.map(_.value).mkString("\n")))
                                 .build()))

                    notificationManager.notify(word.id, notificationBuilder.build())

                    handler.postDelayed(new Runnable() {
                      def run() {
                         notificationManager.cancel(word.id)
                      }
                    }, 40000)

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

  lazy val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(PlayerService.this)

  lazy val notifyId = Iterator.from(Random.nextInt / 2)

  override
  def onStartCommand(intent : Intent, flags : Int, startId : Int) : Int = {
      if (intent.getBooleanExtra("doStop", false)) {
        stop = true
      } else if (stop) {
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
