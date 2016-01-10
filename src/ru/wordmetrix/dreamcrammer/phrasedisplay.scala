package ru.wordmetrix.dreamcrammer

import java.io._
import android.content.{ Context, Intent }

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{ Menu, MenuItem, View, ViewGroup, LayoutInflater }
import android.widget.{
    ToggleButton,
    TextView,
    Button,
    CompoundButton,
    ImageView,
    ArrayAdapter,
    ListView,
    PopupMenu,
    AdapterView,
    GridView
}
import android.graphics.{ BitmapFactory, Bitmap }
import android.support.v4.util.LruCache

class PhraseDisplay(context: DreamCrammerBase, phrase: Phrase)
        extends BaseDisplay(context) {

    override def item(resource: Int = R.layout.phraseitem) =
        super.item(resource)

    override def item(viewgroup: ViewGroup): ViewGroup = {
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

    override def view(resource: Int = R.layout.phraseview): ViewGroup =
        super.view(resource)

    override def whole(viewgroup: ViewGroup): ViewGroup =
        view(wordlist(viewgroup))

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
