package ru.wordmetrix.dreamcrammer

import java.util.Locale

import android.app.{PendingIntent, Service}
import android.content.{IntentFilter, BroadcastReceiver, Context, Intent}
import android.graphics.{Bitmap, BitmapFactory}
import android.media.AudioManager
import android.os.{Binder, Handler, HandlerThread, IBinder, Message, Process}
import android.speech.tts.TextToSpeech
import android.support.v4.app.NotificationCompat.{BigPictureStyle, WearableExtender}
import android.support.v4.app.{NotificationCompat, NotificationManagerCompat, RemoteInput}
import android.widget.Toast
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.PlayerService._
import ru.wordmetrix.dreamcrammer.db._

import scala.annotation.tailrec
import scala.util.{Random, Try}

class PlayerBinder(val service: PlayerService) extends Binder {
  def getService(): PlayerService = {
    return service
  }
}

object PlayerService {

  sealed trait PlayerServiceMessage extends Serializable

  case class PlayerServiceMessageView(id: Int) extends PlayerServiceMessage

  case object PlayerServiceMessageStop extends PlayerServiceMessage

  case object PlayerServiceMessageStart extends PlayerServiceMessage

  case object PlayerServiceMessagePause extends PlayerServiceMessage

  case object PlayerServiceMessageViewLast extends PlayerServiceMessage

  case object PlayerServiceMessageViewNext extends PlayerServiceMessage

  case object PlayerServiceMessageViewPrevious extends PlayerServiceMessage

  case object PlayerServiceMessageResume extends PlayerServiceMessage

  case object PlayerServiceMessageQuery extends PlayerServiceMessage

  case object PlayerServiceMessageRandom extends PlayerServiceMessage

  case class PlayerServiceMessagePhrase(id: Int) extends PlayerServiceMessage

  case object PlayerServiceMessageDefault extends PlayerServiceMessage

  object NotificationIds extends Enumeration {
    val Main = Value
  }

  val EXTRA_VOICE_REPLY = "extra_voice_reply"
}


class PlayerService extends Service with PlayerBase {

  //IntentService("DictLearnService")

  private object myNoisyAudioStreamReceiver extends BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent) {
      intent.getAction() match {
        case AudioManager.ACTION_AUDIO_BECOMING_NOISY =>
          log(s"AudioM = noisy")
          pause()

        case x =>
          log(s"AudioM = $x")
      }
    }
  }

  private val noisyAudioStreamIntentFitler = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

  var textToSpeach: Option[TextToSpeech] = None

  var stop: Boolean = true
  var suspended: Boolean = false
  var lock: AnyRef = new Object
  var callbacks = List[Int => Unit]()

  val binder: IBinder = new PlayerBinder(this)
  val handler: Handler = new Handler();

  val history = new java.util.ArrayList[Int]()

  var history_size = 0

  var history_current = 0

  var servicehandler: Option[Handler] = None

  override def onBind(intent: Intent): IBinder = binder

  def status(): (Boolean, Boolean) = (stop, suspended)

  def pause(): Unit = {
    notificationManager.notify(NotificationIds.Main.id, mainNotificationBuilder(true).build())
    suspended = true
  }

  def resume(): Unit = {
    notificationManager.notify(NotificationIds.Main.id, mainNotificationBuilder(false).build())
    suspended = false
  }

  def exit(): Unit = {
    //notificationManager.notify(NotificationIds.Main.id, mainNotificationBuilder(true).build())
    notificationManager.cancel(NotificationIds.Main.id)
    stop = true
  }

  def seen(callback: Int => Unit): Unit = {
    callbacks = callback :: callbacks
    println("callbacks :: " + callbacks)
  }

  implicit lazy val db: DB = new SQLiteAndroid(this, "taylor.db", true)
  //lazy val convertors =  new Convertors()(db)
  //import convertors._

  def queryPlayerIds() = db.query(
    """select distinct word_id,word_value
      | from picture_word_queue
      |   join picture using(picture_id)
      |   join word_picture using(picture_id)
      | join word using(word_id)
      | where not word_is_seen
      |   and word_track is not null
      |   and not word_value like "% %"
      |   and length(word_value) > 3
      | order by queue_number
      | limit 2000
    """.stripMargin, x => x.columnInt(0)).toArray

  var ids = 0

  trait PlayerServiceIntent {
    protected def activity(intent: Intent) =
      PendingIntent.getActivity(PlayerService.this, {
        ids += 1;
        ids
      }, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    protected def service(intent: Intent) =
      PendingIntent.getService(PlayerService.this, {
        ids += 1;
        ids
      }, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    protected def intent(message: PlayerServiceMessage) = new Intent(PlayerService.this, classOf[PlayerService]) {
      putExtra("message", message)
    }
  }

  object PlayerServiceIntentMessage extends PlayerServiceIntent {
    def apply(message: PlayerServiceMessage) = message match {
      case PlayerServiceMessageStart =>
        activity(new Intent(PlayerService.this, classOf[Player]) {
          putExtra("word_ids", Array[Int]()) //Array[java.lang.Integer]()
        })
      case message =>
        service(intent(message))
    }
  }

  def mainNotificationBuilder(isResume: Boolean) = {
    val notificationBuilder: NotificationCompat.Builder =
      new NotificationCompat.Builder(PlayerService.this)
        .setSmallIcon(R.drawable.play)
        .setContentTitle("English Words")
        .setContentText(s"The thing plays flashcards, $history_size messages has played")
        .setGroup("wordnotification")
        .setNumber(0)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setGroupSummary(true)
        .setContentIntent(PlayerServiceIntentMessage(PlayerServiceMessageStart))
        .addAction(R.drawable.exit, "Exit", PlayerServiceIntentMessage(PlayerServiceMessageStop))

    if (isResume)
      notificationBuilder.addAction(R.drawable.restart, "Resume", PlayerServiceIntentMessage(PlayerServiceMessageResume))
    else notificationBuilder.addAction(R.drawable.pause, "Pause", PlayerServiceIntentMessage(PlayerServiceMessagePause))
  }

  var am: Option[AudioManager] = None

  override def onCreate(): Unit = {
    notificationManager.notify(NotificationIds.Main.id, mainNotificationBuilder(false).build())
    textToSpeach = Some(new TextToSpeech(this.getApplicationContext(), new TextToSpeech.OnInitListener() {
      override def onInit(status: Int): Unit = textToSpeach.foreach { t1 =>
        if (status != TextToSpeech.ERROR) t1.setLanguage(Locale.UK)
      }
    }))

    am = Some(this.getSystemService(Context.AUDIO_SERVICE)) collect {
      case am: AudioManager =>
        am.registerMediaButtonEventReceiver(PlayerServiceRemoteControlReceiver.ComponentName)
        println("RemoteControlReceiver regisered")
        am
      case x =>
        println(s"RemoteControlReceiver wrong = $x")
        null
    }

    registerReceiver(myNoisyAudioStreamReceiver, noisyAudioStreamIntentFitler)

    servicehandler = Some(new Handler(new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND) {
      start()
    }.getLooper()) {

      @tailrec
      def scan(words: List[Word])(f: Word => Word): List[Word] = if (stop) words
      else if (suspended) {
        Thread.sleep(1);
        scan(words)(f)
      } else words match {
        case x :: xs => scan(xs :+ f(x))(f)
        case List() => List()
      }

      @tailrec
      def scanhmm2(word1: Word, word2: Word)(f: Word => Word): List[Word] = if (stop) List()
      else if (suspended) {
        Thread.sleep(1);
        scanhmm2(word1, word2)(f)
      } else {
        scanhmm2(word2, {
          val word: Word = hmm(word1, word2) orElse hmm(word2) orElse hmm() getOrElse (new Word(1)(db))
          log("word : %s", word)
          word.track match {
            case NoData | NeverData => word
            case SomeData(x) => f(word)
          }
        })(f)
      }


      object hmm {
        def query(q1: String, q2: String, w1: Int, w2: Int): Option[Word] = {
          db.query(q1, x => x.columnInt(0), w1, w2) headOption match {
            case Some(x) => db.query(q2, x => new Word(x.columnInt(0))(db), x, w1, w2) match {
              case Stream() => None
              case xs => Some(xs(scala.util.Random.nextInt(xs.size)))
            }

            case None => None
          }
        }

        def apply() = query("select max(word_frequency) from word", "select word_id,word_frequency from word where word_frequency > abs(random()) % ?", 0, 1)

        def apply(w1: Word) = query("select max(hmm1_frequency) from hmm1 where hmm1_word1_id = ?", "select hmm1_emit_id,hmm1_frequency from hmm1 where hmm1_frequency > abs(random()) % ? and hmm1_word1_id=?", w1.id, 1)

        def apply(w1: Word, w2: Word) = query("select max(hmm2_frequency) from hmm2 where hmm2_word1_id = 3 and hmm2_word2_id=4", "select hmm2_emit_id,hmm2_frequency from hmm2 where hmm2_frequency > abs(random()) % 2 and hmm2_word1_id=3 and hmm2_word2_id=4 order by hmm2_frequency", w1.id, w2.id)
      }

      override def handleMessage(msg: Message): Unit = {
        stop = false
        suspended = false

        lock.synchronized {
          scan(scala.util.Random.shuffle(msg.obj.asInstanceOf[List[Word]]))(word => {
            handler.post(new Runnable {
              def run(): Unit = callbacks.map(_ (word.id))
            })

            handler.post(new Runnable {
              def run(): Unit = Toast.makeText(PlayerService.this, "Player: %s".format(word.value), Toast.LENGTH_SHORT).show();
            })

            history.add(word.id)
            history_current = history_size
            history_size = history_size + 1

            notificationManager.notify(NotificationIds.Main.id, mainNotificationBuilder(false).build())

            val vocabularyPendingIntent: PendingIntent = PendingIntent.getActivity(
              PlayerService.this,
              word.id | 0x10000,
              new Intent(PlayerService.this, classOf[Vocabulary]) {
                putExtra("word_id", word.id)
              },
              0)

            val stopPendingIntent: PendingIntent = PendingIntent.getService(
              PlayerService.this,
              0 | 0x20000,
              new Intent(PlayerService.this, classOf[PlayerService]) {
                putExtra("message", PlayerServiceMessageStop)
              },
              0)

            val viewPendingIntent: PendingIntent = PendingIntent.getService(
              PlayerService.this,
              word.id | 0x30000,
              new Intent(PlayerService.this, classOf[PlayerService]) {
                putExtra("message", PlayerServiceMessageView(word.id))
              },
              0)

            val queryPendingIntent: PendingIntent = PendingIntent.getService(
              PlayerService.this,
              word.id | 0x40000,
              new Intent(PlayerService.this, classOf[PlayerService]) {
                putExtra("message", PlayerServiceMessageQuery)
              },
              0)

            val notificationBuilder: NotificationCompat.Builder =
              new NotificationCompat.Builder(PlayerService.this)
                .setSmallIcon(R.drawable.play)
                .setContentTitle(word.value)
                .setContentText(word.ipa getOrElse word.descriptions.mkString("\n"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setGroup("wordnotification")
                .setAutoCancel(true)
                .setContentIntent(vocabularyPendingIntent)
                .extend {
                  new WearableExtender()
                    .addAction(
                      new NotificationCompat.Action.Builder(
                        R.drawable.lookat,
                        s"""view ${word.value}""",
                        viewPendingIntent).build())
                    .addAction(
                      new NotificationCompat.Action.Builder(
                        R.drawable.exit,
                        s"""Stop player""",
                        stopPendingIntent).build())
                    .addAction(
                      new NotificationCompat.Action.Builder(R.drawable.search, "Query the word", queryPendingIntent)
                        .addRemoteInput(new RemoteInput.Builder(EXTRA_VOICE_REPLY)
                          .setLabel("Word?")
                          //.setAllowFreeFormInput (false)
                          .setChoices(history.toArray.takeRight(10).reverse.map{x => new Word(x.asInstanceOf[Int]).value })
                          .build())
                        .build())
                }

            notificationManager.notify(word.id, notificationBuilder.build())

            handler.postDelayed(new Runnable() {
              def run() {
                notificationManager.cancel(word.id)
              }
            }, 120000)

            if (!stop && !suspended) Thread.sleep(500)
            if (!stop && !suspended) play(word)
            if (!stop && !suspended) Thread.sleep(2000)
            if (!stop && !suspended) play(word)
            word
          })
        }

        stopSelf(msg.arg1)
      }
    })
  }

  lazy val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(PlayerService.this)

  lazy val notifyId = Iterator.from(Random.nextInt / 2)

  def extendedWordNotification(word: Word) = {
    val vocabularyPendingIntent: PendingIntent = PendingIntent.getActivity(
      PlayerService.this,
      word.id | 0x10000,
      new Intent(PlayerService.this, classOf[Vocabulary]) {
        putExtra("word_id", word.id)
      },
      0)

    val stopPendingIntent: PendingIntent = PendingIntent.getService(
      PlayerService.this,
      0 | 0x20000,
      new Intent(PlayerService.this, classOf[PlayerService]) {
        putExtra("message", PlayerServiceMessageStop)
      },
      0)

    val queryPendingIntent: PendingIntent = PendingIntent.getService(
      PlayerService.this,
      word.id | 0x40000,
      new Intent(PlayerService.this, classOf[PlayerService]) {
        putExtra("message", PlayerServiceMessageQuery)
      },
      0)

    val randomPendingIntent: PendingIntent = PendingIntent.getService(
      PlayerService.this,
      0x50000,
      new Intent(PlayerService.this, classOf[PlayerService]) {
        putExtra("message", PlayerServiceMessageRandom)
      },
      0)

    val phrasePendingIntent: PendingIntent = PendingIntent.getService(
      PlayerService.this,
      word.id | 0x60000,
      new Intent(PlayerService.this, classOf[PlayerService]) {
        putExtra("message", PlayerServiceMessagePhrase(word.id))
      },
      0)

    def bitmap = word.pictures.view.flatMap(_.bodyOption).flatMap(body => Try(
      Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(body, 0, body.size), 256, 256, true)).toOption)

    val notificationBuilder: NotificationCompat.Builder =
      new NotificationCompat.Builder(PlayerService.this)
        .setSmallIcon(R.drawable.play)
        .setContentTitle(word.value)
        .setContentText(word.ipa getOrElse word.descriptions.mkString("\n"))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setContentIntent(vocabularyPendingIntent)
        .setAutoCancel(true)
        .setContentIntent(PlayerServiceIntentMessage(PlayerServiceMessageView(word.id)))
        .setDeleteIntent(randomPendingIntent)
        .extend {
          val extender = new WearableExtender()

          val extenderBitmaps = bitmap.foldLeft(bitmap.headOption.map { bitmap =>
            extender.setBackground(bitmap)
          }.getOrElse(extender)) {
            case (extender, bitmap) =>
              extender.addPage(
                new NotificationCompat.Builder(PlayerService.this)
                  .setStyle(
                    new BigPictureStyle().bigPicture(bitmap))
                  .extend(new WearableExtender().setHintShowBackgroundOnly(true))
                  .build())
          }

          val extenderPhrases = (word.descriptions.map(_.value) ++ word.phrases.map(_.value)).foldLeft(extenderBitmaps) {
            case (extender, phrase) =>
              extender
                .addPage(
                  new NotificationCompat.Builder(PlayerService.this)
                    .setContentTitle(word.value)
                    .setContentText("phrase:")
                    .setStyle(
                      new NotificationCompat.BigTextStyle().setBigContentTitle("phrase").bigText(phrase))
                    .build())
          }
            .addAction(
              new NotificationCompat.Action.Builder(R.drawable.search, "Query for a word:", queryPendingIntent)
                .addRemoteInput(new RemoteInput.Builder(EXTRA_VOICE_REPLY)
                  .setLabel("Word?")
                  .setChoices(history.toArray.takeRight(10).reverse.map { x => new Word(x.asInstanceOf[Int]).value })
                  .build())
                .build())
            .addAction(
              new NotificationCompat.Action.Builder(
                R.drawable.restart,
                s"""Resume""",
                PlayerServiceIntentMessage(PlayerServiceMessageResume)).build())
            .addAction(
              new NotificationCompat.Action.Builder(
                R.drawable.lookat,
                s"""open ${word.value}""",
                vocabularyPendingIntent).build())

          if (word.phrases.length > 0) {
            val phrase = word.phrases((System.currentTimeMillis() / (60 * 60 * 24 * 1000)) % word.phrases.length toInt).value
            extenderPhrases.addAction(
              new NotificationCompat.Action.Builder(
                R.drawable.speak,
                "Phrase of the day", //phrase,
                phrasePendingIntent).build())
          }


          extenderPhrases.addAction(
            new NotificationCompat.Action.Builder(
              R.drawable.exit,
              s"""Stop player""",
              stopPendingIntent).build())
        }
    notificationBuilder
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    for {
      intent <- Option(intent)
    } {
      Option(intent.getSerializableExtra("message")) match {
        case Some(message) =>
          log(s"message = $message")

          message match {
            case PlayerServiceMessagePause => pause()

            case PlayerServiceMessageStop =>
              notificationManager.cancel(0x40000)
              exit()

            case PlayerServiceMessageResume =>
              notificationManager.cancel(0x40000)
              resume()

            case PlayerServiceMessageQuery =>
              for {
                remoteInput <- Option(RemoteInput.getResultsFromIntent(intent))
                phrase <- Option(remoteInput.getCharSequence(EXTRA_VOICE_REPLY)).map(_.toString)
                _ = log(s"Word value -> $phrase")
                term <- phrase.split( """\W+""")
                word <- Word.query(term)
              } {
                log(s"Word -> $word")
                pause()
                play(word)
                notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)
              }
            case PlayerServiceMessageView(Word(word: Word)) =>
              notificationManager.cancel(word.id)
              log(s"word = $word")
              pause()
              play(word)
              notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)

            case PlayerServiceMessageViewLast =>
              if (history_size > 0) {
                val word = new Word(history.get(history_size-1))
                log(s"word = $word")
                notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)
              }

              pause()

            case PlayerServiceMessageViewPrevious if history_size > 0 =>
              if (history_current > 1 && history_current < history_size) {
                history_current -= 1
              }

              val word = new Word(history.get(history_current))
              log(s"word = $word")
              play(word)
              notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)

              pause()

            case PlayerServiceMessageViewNext if history_size > 0 =>
              if (history_current > 0 && history_current < history_size-1) {
                history_current += 1
              }

              val word = new Word(history.get(history_current))
              log(s"word = $word")
              play(word)
              notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)

              pause()

            case PlayerServiceMessageViewLast =>
              if (history_size > 0) {
                val word = new Word(history.get(history_size-1))
                log(s"word = $word")
                notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)
              }

              pause()

            case PlayerServiceMessageRandom =>
              notificationManager.cancel(0x40000)
              pause()
              val word = new Word(history.get(Random.nextInt(history_size)))
              log(s"word = $word")
              play(word)
              notificationManager.notify(/*word.id | */ 0x40000, extendedWordNotification(word).build)


            case PlayerServiceMessagePhrase(Word(word: Word)) =>
              textToSpeach foreach { case textToSpeach if 0 < word.phrases.length =>
                val phrase = word.phrases((System.currentTimeMillis() / (60 * 60 * 24 * 1000)) % word.phrases.length toInt)
                textToSpeach.speak(phrase.value, TextToSpeech.QUEUE_FLUSH, null)
                log(s"word = $word, phrase=$phrase")
              }
          }
        case _ if (stop) =>
          Toast.makeText(this, "Service has started", Toast.LENGTH_SHORT).show()
          servicehandler foreach { servicehandler =>
            val msg: Message = servicehandler.obtainMessage()
            msg.arg1 = startId
            msg.obj = (Option(intent.getIntArrayExtra("word_ids")).toList.flatMap(_.toList).view ++ queryPlayerIds().toList.view).take(2000).map(new Word(_)(db)).force
            servicehandler.sendMessage(msg)
          }
        case _ =>
          Toast.makeText(this, "Pronounciation has already started playing, new intention ignored", Toast.LENGTH_SHORT).show()
      }


    }

    Service.START_STICKY
  }

  override def onDestroy(): Unit = {
    Toast.makeText(this, "Service has done", Toast.LENGTH_SHORT).show()
    textToSpeach.foreach { tts =>
      tts.stop()
      tts.shutdown()
      textToSpeach = None
    }

    am.foreach {
      case am: AudioManager =>
        am.unregisterMediaButtonEventReceiver(PlayerServiceRemoteControlReceiver.ComponentName)
        this.am = None
    }

    unregisterReceiver(myNoisyAudioStreamReceiver)
  }
}
