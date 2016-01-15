package ru.wordmetrix.dreamcrammer

import android.app.{PendingIntent, Activity, SearchManager}
import android.graphics.{BitmapFactory, Bitmap}
import android.os.{ Bundle }
import android.support.v4.app.{NotificationManagerCompat, NotificationCompat}
import android.support.v4.app.NotificationCompat.{BigPictureStyle, WearableExtender}
import android.view.ViewGroup
import ru.wordmetrix.dreamcrammer.Player
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.Context
import android.view.{ View, LayoutInflater }
import android.widget.{ ArrayAdapter, TextView, SearchView, ListView }
import android.content.{ Intent, IntentFilter, BroadcastReceiver }
import android.support.v4.widget.DrawerLayout
import android.support.v4.content.LocalBroadcastManager

import scala.util.Try

object IDs {
    val id = Iterator.iterate(1)(x => x + 1)
}

class Vocabulary
        extends FieldBase[Word] with MenuVocabulary
        with WordEditDialog.Listener
        with DescriptionAddDialog.Listener
        with PhraseAddDialog.Listener
        with PictureBaseDialog.Listener[Word] {
    val appid = IDs.id.next

    def doEdit() = field.map(new WordEditDialog(_).
        show(getSupportFragmentManager(), "wordedit"))

    def addDescription() = field.map(
        new DescriptionAddDialog(_).show(getSupportFragmentManager(),
            "descriptionadd"))

    def addPhrase() = field.map(
        new PhraseAddDialog(_).show(getSupportFragmentManager(),
            "phraseadd"))

    def addPicture() = field.map(
        new PictureAddDialog(_).show(getSupportFragmentManager(),
            "pictureadd"))

    def downloadTrack(word: Word) = startService(new Intent(this, classOf[TaskService]) {
        putExtra("task", new Task(Task.Kind.Track, word.id, "", "", ""))
    })

    def downloadTrack() = field.map(downloadTrack)

    def downloadPronunciation(word: Word) = startService(new Intent(this, classOf[TaskService]) {
        putExtra("task", new Task(Task.Kind.Pronunciation, word.id, "", "", ""))
    })
    def downloadPronunciation() = field.map(downloadPronunciation)

    def downloadPicture(word: Word) = startService(new Intent(this, classOf[TaskService]) {
        putExtra("task", new Task(Task.Kind.Picture, word.id, "", "", ""))
    })
    def downloadPicture() = field.map(downloadPicture)

    def doAddField(view: View, query: String) = {
        val word = new Word(query)(db).register()
        resync(word)
        true
    }

    def resync(word: Word) {
        downloadPicture(word)
        downloadPronunciation(word)
        downloadTrack(word)
    }

    override def layout = R.layout.vocabulary

    override val layout_item: Int = R.layout.worditem_left

    def publish(intent: Intent) = publish(new Word(getIntent().getIntExtra("word_id", 1))(db))
    def publish(word: Word) = {
        this.field = Some(word)
        sync(word)
        new WordDisplay(this, word).whole(findViewById(android.R.id.content).asInstanceOf[ViewGroup].getChildAt(0).asInstanceOf[ViewGroup])
    }

    def sync(word: Word) = {
        log("sync track: %s", word.track)
        log("sync ipa: %s", word.ipa)
        log("sync pictures: %s", word.pictures)
        word.track match {
            case NeverData => downloadTrack(word)
            case _         => {}
        }

        word.ipa match {
            case NeverData => downloadPronunciation(word)
            case _         => {}
        }

        word.pictures match {
            case List() => downloadPicture(word)
            case _      => {}
        }

        word.lastseen()
    }

    def doFirst(view: View, word: Word) = {
        taskasync {
            log("doFirst has started async")
            for {
                (q1, q2, q3, q4, q5) <- db.query("select exercise_id, exercise_name, exercise_grade, exercise_begin, queue_number from exercise", x => (x.columnInt(0), x.columnString(1), x.columnDouble(2), x.columnInt(3), x.columnDouble(4)))
            } {
                log("%s %s %s %s %s", q1, q2, q3, q4, q5)
            }

            convertors.word2phrases.ahead(word)
            convertors.phrase2words.ahead(word)
            convertors.word2pictures.ahead(word)
            convertors.picture2words.ahead(word)
            convertors.word2descriptions.ahead(word)
            convertors.description2words.ahead(word)

            for {
                (q1, q2, q3, q4, q5) <- db.query("select exercise_id, exercise_name, exercise_grade, exercise_begin, queue_number from exercise", x => (x.columnInt(0), x.columnString(1), x.columnDouble(2), x.columnInt(3), x.columnDouble(4)))
            } {
                log("%s %s %s %s %s", q1, q2, q3, q4, q5)
            }

            log("%s", db.query("select queue_number from word_phrase_queue where word_id=?", _.columnInt(0), word.id).toList)
            log("%s", db.query("select queue_number from phrase_word_queue where phrase_id in (select phrase_id from word_phrase where word_id=?)", _.columnInt(0), word.id).toList)
            log("%s", db.query("select queue_number from word_picture_queue where word_id=?", _.columnInt(0), word.id).toList)
            log("%s", db.query("select queue_number from picture_word_queue where picture_id in (select picture_id from word_picture where word_id=?)", _.columnInt(0), word.id).toList)
            log("%s", db.query("select queue_number from word_description_queue where word_id=?", _.columnInt(0), word.id).toList)
            log("%s", db.query("select queue_number from description_word_queue where description_id in (select description_id from word_description where word_id=?)", _.columnInt(0), word.id).toList)
        } {
            log("finish of doFirst")
        }
    }

    def search_query(query: String): Stream[Word] = db.query("""select word_id, word_value from word where word_value like ? """, x => new Word(x.columnInt(0))(db) {
        val _value = x.columnString(1)
        override lazy val value = _value
    }, query + '%')

    def displayItem(itemview: ViewGroup, word: Word): ViewGroup = {
        itemview.findViewById(R.id.word_value).asInstanceOf[TextView].setText(word.value)
        itemview
    }

    override def onResume(): Unit = {
        log("Resume Vocabulary")
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastreceiver, new IntentFilter(Task.Message.Reload.toString))

    }

    lazy val broadcastreceiver = new BroadcastReceiver() {
        override def onReceive(context: Context, intent: Intent) = {
            log("Broadcast recieved about task %s for app %s", intent.getIntExtra("kind", 0), appid)
            intent.getIntExtra("kind", 0) match {
                case _ => {
                    if (field.exists(_.id == intent.getIntExtra("id", 0))) doReload()
                }
            }
        }
    }

    override def onPause(): Unit = {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastreceiver);
        super.onPause();
    }

}
