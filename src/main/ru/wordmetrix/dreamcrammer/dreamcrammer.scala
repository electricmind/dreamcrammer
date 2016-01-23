package ru.wordmetrix.dreamcrammer

import java.util.Locale

import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v4.app.FragmentActivity
import android.view.ViewGroup
import android.widget.{CompoundButton, ImageView, ToggleButton}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.Success

abstract
class DreamCrammerBase extends FragmentActivity with MenuBase with PlayerBase {

  def layout: Int

  lazy val preferences = new PreferencesBase("DreamCrammer", this)
  lazy val db: DB = new SQLiteAndroid(this, "taylor.db", true)
  lazy val convertors = new Convertors()(db)


  var textToSpeach: Option[TextToSpeech] = None

  override  def onStop() = {
    super.onStop()
    db.close()
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState) 
    textToSpeach = Some(new TextToSpeech(this.getApplicationContext(), new TextToSpeech.OnInitListener() {
        override def onInit(status: Int): Unit = textToSpeach.foreach { t1 =>
          if (status != TextToSpeech.ERROR) t1.setLanguage(Locale.UK)
        } 
    }))
    setContentView(layout)
  }

  override def onRestart() {
    super.onRestart()
    textToSpeach = Some(new TextToSpeech(this.getApplicationContext(), new TextToSpeech.OnInitListener() {
      override def onInit(status: Int): Unit = textToSpeach.foreach { t1 =>
        if (status != TextToSpeech.ERROR) t1.setLanguage(Locale.UK)
      }
    }))
  }

  override def onPause() {
    textToSpeach.foreach { tts =>
        tts.stop()
        tts.shutdown()
        textToSpeach = None
    }

    super.onPause()
  }

  def display(viewGroup: ViewGroup, picture: Picture) = {
    val imageView: ImageView = viewGroup.findViewById(R.id.picture_body).asInstanceOf[ImageView]

    picture.bodyOption.map(body =>
      imageView.setImageBitmap(BitmapFactory.decodeByteArray(body, 0, body.size))
    )

    viewGroup
  }


  def display(viewGroup: ViewGroup, word: Word) = {
    log("display %s %s", viewGroup, word)
    new WordDisplay(this, word).view(viewGroup)
    viewGroup.findViewById(R.id.word_is_seen).asInstanceOf[ToggleButton].setChecked(word.is_seen)
    viewGroup.findViewById(R.id.word_is_seen).asInstanceOf[ToggleButton].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = word.is_seen(isChecked)
    })

    viewGroup
  }


}

