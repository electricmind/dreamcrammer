package ru.wordmetrix.dreamcrammer
import android.widget.ImageView
import android.content.{Context}
import android.util.AttributeSet
import android.view.{MenuInflater,MenuItem, View}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

class ScaledImageView(context : Context, attrs : AttributeSet, defStyle : Int) extends ImageView(context, attrs, defStyle) {
    log("ScaledImageMenu attrs: %s", attrs)

    def this(context : Context, attrs : AttributeSet) = this(context, attrs, 0)

    def this(context : Context) = this(context, null, 0)

    override
    def onMeasure (widthMeasureSpec : Int, heightMeasureSpec : Int) : Unit = {
        log("onMeasure", widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}