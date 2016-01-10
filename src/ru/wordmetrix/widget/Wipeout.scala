package ru.wordmetrix.widget

import java.lang.Math.{abs, max, min}
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import android.app.Activity
import android.content.Context
import android.support.v4.view.GestureDetectorCompat
import android.util.AttributeSet
import android.view.{GestureDetector, MotionEvent}
import android.widget.FrameLayout
import ru.wordmetrix.log
import android.view.View

class Wipeout(context: Context, attrs: AttributeSet, defStyle: Int)
        extends FrameLayout(context, attrs, defStyle) {
    log("Wipeout attrs: %s", attrs)

    lazy val (original,slide) = {
        val original = getChildAt(0)
        val slide = new FrameLayout(context, attrs, defStyle)
        removeAllViews()
        log("Slide / orginal : %s %s", slide, original)
        addView(slide)
        slide.addView(original)
        (original, slide)
    }

    def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

    def this(context: Context) = this(context, null, 0)

    val orientation = Option(attrs) match {
        case None => 0
        case Some(x) => x.getAttributeIntValue(
            "http://schemas.android.com/apk/res/android", "orientation", 0)
    }

    log("orientation: %s", orientation)
    val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    val mDetector = new GestureDetectorCompat(context,
        new GestureDetector.SimpleOnGestureListener() {

            var postponed: Option[ScheduledFuture[_]] = None
            def postpone(f: => Unit) {
                postponed.map(_.cancel(true))

                log("set up task")
                postponed = Some(
                    scheduler.schedule(new Runnable() {
                        def run() {
                            log("Timeout!! q")
                            context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
                                def run() {
                                    log("Timeout!!")
                                    f
                                }
                            })
                        }
                    }, 100, TimeUnit.MILLISECONDS))

            }

            override def onScroll(e1: MotionEvent, e2: MotionEvent,
                                  distanceX: Float,
                                  distanceY: Float): Boolean = {
                //begin scroll motion
                log("on Scroll wipeout: %s %s %s %s", e1, e2, distanceX, distanceY)

                //                val view = Wipeout.this.getChildAt(0)
                val height = Wipeout.this.getHeight()
                val width = Wipeout.this.getWidth()

                (orientation, distanceX, distanceY) match {
                    case (1, distanceX, distanceY) => {
                        slide.setTranslationY(
                            min(slide.getTranslationY() - distanceY, 0))
                        postpone(onFling(null, null, 0, 0))
                    }
                    case (0, distanceX, distanceY) => {
                        slide.setTranslationX(
                            max(slide.getTranslationX() - distanceX, 0))
                        postpone(onFling(null, null, 0, 0))
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
                                 velocityx: Float, velocityy: Float) = {
                // complete motion
                log("fling %f %f %f".format(velocityx, velocityy, velocityx / velocityy))
                postponed.map(_.cancel(true))
                postponed = None
                
                (orientation, velocityx, velocityy, abs(velocityx / velocityy)) match {
                    case (1, _, side, ratio) if (side < 0 && ratio < 0.5) => {
                        slide.animate().y(-getHeight()).setDuration(abs((side / getHeight())).toInt).start()
                        onWipe(Wipeout.this)
                    }
                    case (0, side, _, ratio) if (side > 0 && ratio > 2) => {
                        slide.animate().x(getWidth()).setDuration(abs((side / getWidth())).toInt).start()
                        onWipe(Wipeout.this)
                    }
                    case (_, _, _, _) => {slide.animate().y(0).x(0).start()
                        original.performClick()}
                }
                false
            }

            override def onLongPress(e: MotionEvent) = {}

            override def onShowPress(e: MotionEvent) = {}

            override def onSingleTapUp(e: MotionEvent) = {
                log("WipeUp onSingleTap");
                //                doTranslate(null)
                true
            }

        })

    var onWipe = (v : View) => {}
    
    def setOnWipe(f : View => Unit) = {
        onWipe = f
    }
    
    override def onTouchEvent(event: MotionEvent) = {
                log("on Touch: %s", event)

        mDetector.onTouchEvent(event)
        false
        //        super.onTouchEvent(event)
    }

    override def onInterceptTouchEvent(ev: MotionEvent) = {
        log("Intercept %s", ev)
        mDetector.onTouchEvent(ev)
        log("Intercept qq")
        false
    }
}