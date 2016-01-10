package ru.wordmetrix.dreamcrammer
import android.widget.FrameLayout
import android.content.{Context}
import android.util.AttributeSet

import android.view.{View,GestureDetector,MotionEvent}
import android.support.v4.view.{MotionEventCompat,GestureDetectorCompat}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

class InterceptTouchEventListener {
      def onInterceptTouchEvent(ev : MotionEvent) : Boolean = false
}

class OnInterceptTouchEvent(context : Context, attrs : AttributeSet, defStyle : Int) extends FrameLayout(context, attrs, defStyle) {
    log("OnItereceptTouchEvent attrs: %s", attrs)

    def this(context : Context, attrs : AttributeSet) = this(context, attrs, 0)

    def this(context : Context) = this(context, null, 0)

    var onInterceptTouchEventListener : Option[InterceptTouchEventListener] = None

    def setInterceptTouchEventListener(listener : InterceptTouchEventListener) = {
        onInterceptTouchEventListener = Some(listener)
    }

    override
    def onInterceptTouchEvent(ev : MotionEvent) = {
        log("Intercept %s",ev)
        onInterceptTouchEventListener match {
            case Some(x) => x.onInterceptTouchEvent(ev)
            case None => super.onInterceptTouchEvent(ev)
        }
    }
}