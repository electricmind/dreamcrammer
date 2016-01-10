package ru.wordmetrix.widget
import android.widget.LinearLayout
import android.content.{ Context }
import android.util.AttributeSet
import android.view.{ GestureDetector, MotionEvent }
import android.support.v4.view.GestureDetectorCompat
//import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

class VerticalSlidePane(context: Context, attrs: AttributeSet, defStyle: Int)
        extends LinearLayout(context, attrs, defStyle) {
    log("VerticalSlidePane attrs: %s", attrs)

    def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

    def this(context: Context) = this(context, null, 0)

    val orientation = Option(attrs) match {
        case None => 0
        case Some(x) => x.getAttributeIntValue(
            "http://schemas.android.com/apk/res/android", "orientation", 0)
    }

    log("orientation: %s", orientation)

    val mDetector = new GestureDetectorCompat(context,
        new GestureDetector.SimpleOnGestureListener() {
            override def onScroll(e1: MotionEvent, e2: MotionEvent,
                                  distanceX: Float,
                                  distanceY: Float): Boolean = {
                //                log("on Scroll: %s %s %s %s",e1,e2,distanceX,distanceY)
                val view = VerticalSlidePane.this.getChildAt(0)
                val height = VerticalSlidePane.this.getHeight()
                val width = VerticalSlidePane.this.getWidth()

                (orientation, distanceX, distanceY) match {
                    case (1, distanceX, distanceY) if (Math.abs(distanceX / distanceY) < 0.1) => {
                        //                        val view = findViewById(R.id.toppanel)
                        //                        log("!! set")
                        view.setLayoutParams(
                            new LinearLayout.LayoutParams(
                                view.getLayoutParams().width,
                                Math.max(20,
                                    Math.min(height,
                                        (view.getHeight() - distanceY).toInt))))
                    }
                    case (0, distanceX, distanceY) if (Math.abs(distanceX / distanceY) > 10) => {
                        //                        val view = findViewById(R.id.toppanel)
                        //                        log("!! set")
                        view.setLayoutParams(
                            new LinearLayout.LayoutParams(
                                Math.max(
                                    20,
                                    Math.min(width,
                                        (view.getWidth() - distanceX).toInt)),
                                view.getLayoutParams().height))
                    }
                    case _ => true

                }
                super.onScroll(e1, e2, distanceX, distanceY)
            }

            override def onDown(e: MotionEvent) = {
                //                prevdelta = 0
                true
            }

            override def onFling(e1: MotionEvent, e2: MotionEvent,
                                 velocityx: Float, velocityy: Float) = false

            override def onLongPress(e: MotionEvent) = {}

            override def onShowPress(e: MotionEvent) = {}

            override def onSingleTapUp(e: MotionEvent) = {
                log("onSingleTap");
                //                doTranslate(null)
                false
            }

        })

    override def onTouchEvent(event: MotionEvent) = {
        //        log("on Touch: %s", event)

        mDetector.onTouchEvent(event)
        true
        //        super.onTouchEvent(event)
    }

    override def onInterceptTouchEvent(ev: MotionEvent) = {
        //        log("Intercept %s",ev)
        mDetector.onTouchEvent(ev)
        false
    }
}