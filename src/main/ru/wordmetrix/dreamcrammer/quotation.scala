package ru.wordmetrix.dreamcrammer

import java.util.Locale

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.view.{View, ViewGroup}
import android.widget.TextView
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Quotation extends FieldBase[Phrase] with MenuQuotation with PhraseBaseDialog.Listener[Phrase] {

  def doFirst(view: View, phrase: Phrase) = taskasync {
    convertors.phrase2words.ahead(phrase)
    convertors.word2phrases.ahead(phrase)
  } {}

  def doAddField(view: View, query: String): Boolean = {
    // ticket : implement a handler to add new phrase
    log("Phrase addition has not been implemented yet")
    false
  }

  def doEdit() = field.map(new PhraseEditDialog(_).show(getSupportFragmentManager(), "phraseedit"))

  /*     def addPhrase() = field match {
             case Some(x) => new PhraseAddDialog(x).show(getSupportFragmentManager(), "wordadd")
             case None => log("Dialog has been called without any phrase")
         }*/

  override
  def layout = R.layout.quotation

  def publish(intent: Intent) = publish(new Phrase(getIntent().getIntExtra("phrase_id", 1))(db))

  def publish(phrase: Phrase) = {
    this.field = Some(phrase)
    new PhraseDisplay(this, phrase).whole(findViewById(android.R.id.content).asInstanceOf[ViewGroup].getChildAt(0).asInstanceOf[ViewGroup])
  }

  def search_query(query: String): Stream[Phrase] = db.query(
    """select phrase_id, phrase_value from phrase where phrase_value like ? order by phrase_value limit 40""", x => new Phrase(x.columnInt(0))(db) {
      val _value = x.columnString(1)
      override lazy val value = _value
    }, '%' + query + '%')

  def displayItem(itemview: ViewGroup, phrase: Phrase): ViewGroup = {
    itemview.findViewById(R.id.phrase_value).asInstanceOf[TextView].setText(phrase.value)
    itemview
  }

  override
  val layout_item: Int = R.layout.phraseitem_left
}
