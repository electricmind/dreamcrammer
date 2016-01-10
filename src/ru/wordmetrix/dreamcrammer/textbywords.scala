package ru.wordmetrix.dreamcrammer

import android.widget.{ FrameLayout, LinearLayout, PopupMenu, TextView }
import android.content.{ Context }
import android.util.AttributeSet
import android.view.{ View, ViewGroup, LayoutInflater, Gravity }
import android.content.Intent
import android.view.{ View, GestureDetector, MotionEvent }
import android.support.v4.view.{ MotionEventCompat, GestureDetectorCompat }
import android.app.SearchManager
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import ru.wordmetrix.nlp.NLP._
import ru.wordmetrix.widget._
import ru.wordmetrix.widget.FloatLayout

class TextByWords(context: Context, attrs: AttributeSet, defStyle: Int)
        extends FloatLayout(context, attrs, defStyle) {
    log("FrameMenu attrs: %s", attrs)

    val db = context.asInstanceOf[DreamCrammerBase].db

    def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

    def this(context: Context) = this(context, null, 0)

    var listener: String => Boolean = (word) => { true }

    def setText(x: String) = {
        val inflater = context.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

        removeAllViews()

        for (word <- x.tokenize ) {
            val viewgroup = inflater.inflate(R.layout.worditembutton, null)
                .asInstanceOf[ViewGroup]

            val wordview = viewgroup.findViewById(R.id.word_value)
                .asInstanceOf[TextView]

            wordview.setText(word)

            wordview.setOnClickListener(new View.OnClickListener {
                def onClick(view: View) = {
                    context.startActivity(
                        new Intent(Intent.ACTION_SEARCH,
                                null,
                                context,
                                classOf[Vocabulary]) {
                            putExtra(SearchManager.QUERY, word.toLowerCase())
                        })
                }
            })

            wordview.setOnLongClickListener(new View.OnLongClickListener {
                def onLongClick(view: View) = listener(word.toLowerCase())
            })

            addView(viewgroup)
        }
    }

    def setListener(f: String => Boolean) = listener = f

}
