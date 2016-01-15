package ru.wordmetrix.dreamcrammer

import java.util.concurrent._

import android.app.PendingIntent
import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.graphics.{Bitmap, BitmapFactory}
import android.os.{Bundle, Handler, IBinder}
import android.support.v4.app.NotificationCompat.{BigPictureStyle, WearableExtender}
import android.support.v4.app.{NotificationCompat, NotificationManagerCompat}
import android.support.v4.util.LruCache
import android.view.{LayoutInflater, Menu, MotionEvent, View, ViewGroup}
import android.widget.{ArrayAdapter, ListView, TextView, ToggleButton}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import scala.util.Try

/*
E/AndroidRuntime( 1538): java.lang.IllegalStateException: The content of the adapter has changed but ListView did not receive a notification. Make sure the content of your adapter is not modified from a background thread, but only from the UI thread. [in ListView(2131165200, class android.widget.ListView) with Adapter(class ru.wordmetrix.dreamcrammer.Player$$anon$6$$anon$1)]
E/AndroidRuntime( 1538):        at android.widget.ListView.layoutChildren(ListView.java:1538)
*/

class Player extends DreamCrammerBase with MenuPlayer {
  var service: Option[PlayerService] = None
  var bound: Boolean = false;

  def doReload() = {
    // ticket : implement player reload
    log("This feature hasn't implemented yet")
  }

  def doDelete() = {
    // ticket : implement player delete
    log("This feature hasn't implemented yet")
  }

  override
  def layout = R.layout.player

  override
  def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    startService(new Intent(this, classOf[PlayerService]) {
      putExtra("word_ids", getIntent().getIntArrayExtra("word_ids"))
    })

    findViewById(R.id.onintercepttouchevent).asInstanceOf[ru.wordmetrix.dreamcrammer.OnInterceptTouchEvent].setInterceptTouchEventListener(new InterceptTouchEventListener() {
      override
      def onInterceptTouchEvent(ev: MotionEvent): Boolean = {
        doSuspendForAwhile(null)
        false
      }
    })
  }

  override
  def doPauseResume(view: View): Unit = (if (view.asInstanceOf[ToggleButton].isChecked) doResume _ else doPause _ )(view)

  override
  def doStop(view: View): Unit = finish()

  override
  def doExit(view: View): Unit = {
    service match {
      case Some(x) => x.exit();
      case None => {}
    }
    finish()
  }

  def doResume(view: View) = service match {
    case Some(x) => x.resume()
    case None => {}
  }

  def doPause(view: View) = service match {
    case Some(x) => x.pause()
    case None => {}
  }

  val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1);

  var postponed: Option[ScheduledFuture[_]] = None

  def doSuspendForAwhile(view: View) {

    log("Do suspend for a while %s", view)

    def schedule() = scheduler.schedule(new Runnable() {
      def run() = {
        log("Pustponed stop has started")
        runOnUiThread(new Runnable() {
          def run() {
            doResume(view)
            pauseresumebutton.setChecked(true)
            postponed.get.cancel(true)
            postponed = postponed match {
              case Some(x) => x.cancel(true); None
              case None => None
            }
          }
        })
      }
    }, 10, TimeUnit.SECONDS)

    service match {
      case None => {}
      case Some(x) => (x.status, postponed) match {
        case ((_, true), None) => pauseresumebutton.setChecked(false)
        case ((_, false), _) | ((_, true), Some(_)) => {
          pauseresumebutton.setChecked(false)
          postponed = postponed match {
            case Some(task) => {
              task.cancel(true)
              Some(schedule())
            }

            case None => {
              doPause(view)
              Some(schedule())
            }
          }
        }
      }
    }
  }

  def publish(word: Word) {
    findViewById(R.id.currentword).asInstanceOf[ViewGroup].removeAllViews()

    findViewById(R.id.currentword).asInstanceOf[ViewGroup].addView(
      new WordDisplay(this, word, true).view()
    )

    findViewById(R.id.currentpicture).asInstanceOf[ViewGroup].removeAllViews()

    findViewById(R.id.currentpicture).asInstanceOf[ViewGroup].addView(
      scala.util.Random.shuffle(word.pictures).headOption match {
        case Some(picture) => new PictureDisplay(this, picture).view()
        case None => new WordDisplay(this, word, true).view()
      }
    )
  }

  val serviceconnection: ServiceConnection = new ServiceConnection() {
    override
    def onServiceConnected(className: ComponentName, binder: IBinder): Unit = {
      service = Some(binder.asInstanceOf[PlayerBinder].getService())
      service match {
        case Some(x) => {
          //ticket : manage queue of listened words

          val historyadapter = new ArrayAdapter(Player.this, android.R.layout.simple_list_item_1, x.history) {
            val cache = new LruCache[Int, Word](100) {
              override def create(id: Int): Word = new Word(id)(db)
            }

            override
            def getView(position: Int, convertView: View, parent: ViewGroup): View = {
              val itemview = convertView match {
                case null => Player.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.worditem_left, null).asInstanceOf[ViewGroup]
                case v: View => v
              }

              itemview.findViewById(R.id.word_value).asInstanceOf[TextView].setText(cache.get(getItem(position)).value)
              itemview.setOnClickListener(new View.OnClickListener {
                override
                def onClick(v: View) {
                  publish(cache.get(getItem(position)))
                }
              })
              itemview
            }
          }

          Player.this.findViewById(R.id.history).asInstanceOf[ListView].setAdapter(historyadapter)
          Player.this.findViewById(R.id.history_size).asInstanceOf[TextView].setText("%s (%d)".format("", x.history_size))

          x.seen(id => if (postponed == None) {
            val word = new Word(id)(db)
            //findViewById(R.id.last_word).asInstanceOf[TextView].setText( word.value )
            //history_size = history_size + 1

            Player.this.findViewById(R.id.history_size).asInstanceOf[TextView].setText("%s (%d)".format(word.value, x.history_size))
            val inflater: LayoutInflater = Player.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

            historyadapter.notifyDataSetChanged()
            Player.this.findViewById(R.id.history).asInstanceOf[ListView].smoothScrollByOffset(1)
            publish(word)
            println(s"======== ${word.id}")


          })

          x.status match {
            // ticket: null in pauseresumebutton
            case (stoped, suspended) => pauseresumebutton.setChecked(!suspended)
          }
        }

        case None => {}
      }

    }

    override
    def onServiceDisconnected(arg0: ComponentName): Unit = {

      service = None
    }
  }

  val handler = new Handler()

  override
  def onStart(): Unit = {
    super.onStart();
  }

  override
  def onCreateOptionsMenu(menu: Menu): Boolean = {
    val ret = super.onCreateOptionsMenu(menu)
    bindService(new Intent(this, classOf[PlayerService]), serviceconnection, Context.BIND_AUTO_CREATE)
    ret
  }

  override
  def onStop(): Unit = {
    super.onStop();
    service = service match {
      case Some(x) => unbindService(serviceconnection); None
      case None => None
    }
  }

}
