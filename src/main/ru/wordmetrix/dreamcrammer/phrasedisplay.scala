package ru.wordmetrix.dreamcrammer

import java.util.Locale

import android.speech.tts.TextToSpeech
import android.view.{View, ViewGroup}
import android.widget.{Button, TextView}
import ru.wordmetrix.dreamcrammer.db._
import scala.concurrent.ExecutionContext.Implicits.global

class PhraseDisplay(context: DreamCrammerBase, phrase: Phrase)
  extends BaseDisplay(context) {

  override def item(resource: Int = R.layout.phraseitem) =
    super.item(resource)

  override def item(viewgroup: ViewGroup): ViewGroup = {
    speakButton(viewgroup)
    viewgroup.findViewById(R.id.phrase_value) match {
      case null => {}
      case x: TextView => {
        x.setText(phrase.value)
      }
      case x: TextByWords => {
        x.setText(phrase.value)
        x.setListener(value => {
          Word.query(value)(context.db).map(word => {
            word.join(phrase)

            if (word.disable(phrase)) {
              //convertors.word2phrases.register(this);
              //convertors.phrases2word.register(this);

              word.disable(phrase, false)
              word.is_seen(false)
              Option(viewgroup.findViewById(R.id.phrase_words))
                .map({
                  case viewgroup: ViewGroup =>
                    viewgroup.addView(
                      new WordDisplay(context, word)
                        .item(R.layout.worditem_line))
                })
            } else {
              word.disable(phrase, true);
              wordlist(viewgroup)
            }
          })
          true
        })
      }
    }
    viewgroup
  }

  override def view(resource: Int = R.layout.phraseview): ViewGroup = {
    super.view(resource)
  }

  private def speakButton(view: View) = {
    Option(view.findViewById(R.id.phrase_track)) foreach {
      case button: Button =>
        button.setOnClickListener(new View.OnClickListener() {
          override def onClick(view: View): Unit = context.textToSpeach foreach { t1 =>
              t1.speak(phrase.value, TextToSpeech.QUEUE_FLUSH, null)
            }
        })
    }
  }
  override def whole(viewgroup: ViewGroup): ViewGroup = {
    speakButton(viewgroup)
    view(wordlist(viewgroup))
  }


  def wordlist(viewgroup: ViewGroup) = {
    val wordlist = viewgroup.findViewById(R.id.phrase_words)
      .asInstanceOf[ViewGroup]

    wordlist.removeAllViews()
    for (word <- context.convertors.phrase2words.feed(phrase)) {
      wordlist.addView(new WordDisplay(context, word)
        .item(R.layout.worditem_line))
    }
    viewgroup

  }

}
