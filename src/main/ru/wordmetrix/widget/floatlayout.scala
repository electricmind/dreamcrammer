package ru.wordmetrix.widget
import android.view.{ View, Gravity }
import android.widget.FrameLayout
import java.lang.Math.max
import android.graphics.Rect
import ru.wordmetrix._
import android.util.AttributeSet
import android.content.{ Context }

class FloatLayout(context: Context, attrs: AttributeSet, defStyle: Int)
        extends FrameLayout(context, attrs, defStyle) {
    log("FloatLayout attrs: %s", attrs)

    def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

    def this(context: Context) = this(context, null, 0)

    lazy val childs = (0 until getChildCount())
        .map(getChildAt).filter(_.getVisibility() != View.GONE)

    override def onMeasure(widthMeasureSpec: Int,
                           heightMeasureSpec: Int): Unit = {
        val maxWidth = View.MeasureSpec.getSize(widthMeasureSpec)

        (View.MeasureSpec.getMode(widthMeasureSpec) match {
            case View.MeasureSpec.UNSPECIFIED => childs.foldLeft(
                (0, 0, 0, 0, 0))({
                    case ((left, top, rh, width, childState), child) => {

                        measureChildWithMargins(child,
                            View.MeasureSpec.makeMeasureSpec(
                                View.MeasureSpec.getSize(widthMeasureSpec),
                                View.MeasureSpec.AT_MOST),
                            0,

                            View.MeasureSpec.makeMeasureSpec(
                                View.MeasureSpec.getSize(heightMeasureSpec),
                                View.MeasureSpec.AT_MOST),
                            0)

                        val lp = child.getLayoutParams()
                            .asInstanceOf[FrameLayout.LayoutParams]

                        (left + child.getMeasuredWidth() + lp.leftMargin +
                            lp.rightMargin,
                            0,
                            max(rh, child.getMeasuredHeight() + lp.topMargin +
                                lp.bottomMargin),
                            0,
                            View.combineMeasuredStates(childState,
                                child.getMeasuredState()))
                    }
                })

            case _ => childs.foldLeft((0, 0, 0, 0, 0))({
                case ((left, top, rh, width, childState), child) => {

                    measureChildWithMargins(child,
                        View.MeasureSpec.makeMeasureSpec(
                            View.MeasureSpec.getSize(widthMeasureSpec),
                            View.MeasureSpec.AT_MOST),
                        0,
                        View.MeasureSpec.makeMeasureSpec(
                            View.MeasureSpec.getSize(heightMeasureSpec),
                            View.MeasureSpec.AT_MOST),
                        0)

                    val lp = child.getLayoutParams()
                        .asInstanceOf[FrameLayout.LayoutParams]

                    val w = child.getMeasuredWidth() + lp.leftMargin +
                        lp.rightMargin
                    val h = child.getMeasuredHeight() + lp.topMargin +
                        lp.bottomMargin

                    val (w1, h1, rh1, width1) = (left + w) match {
                        case r if (r <= maxWidth) => (r, top, max(rh, h), width)
                        case r => (w, (top + rh), h,
                            max(width, left))
                    }

                    (w1, h1, rh1, width1, View.combineMeasuredStates(childState,
                        child.getMeasuredState()))
                }
            })
        }) match {
            case (left, height, rowheight, width, childState) =>
                setMeasuredDimension(
                    View.resolveSizeAndState(max(left, width),
                        widthMeasureSpec, childState),
                    View.resolveSizeAndState(height + rowheight,
                        heightMeasureSpec,
                        childState << View.MEASURED_HEIGHT_STATE_SHIFT))
        }
    }

    override def onLayout(
        changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
        val right = r - getPaddingRight()
        val left = l + getPaddingLeft()
        val top = t + getPaddingTop()
        val bottom = b - getPaddingBottom()

        childs.foldLeft((left, top, 0))({
            case ((l, t, rh), child) => {

                val lp = child.getLayoutParams()
                    .asInstanceOf[FrameLayout.LayoutParams]
                val w = child.getMeasuredWidth() +
                    lp.leftMargin + lp.rightMargin
                val h = child.getMeasuredHeight() +
                    lp.topMargin + lp.bottomMargin

                val (l1, r1, t1, b1, rh1) = (l + w) match {
                    case r if r <= right => (l, r, t, t + h, max(rh, h))
                    case r => (left, left + w, t + rh,
                        t + rh + h, h)
                }

                val containerRect = new Rect(l1, t1, r1, b1)
                val childRect = new Rect()

                Gravity.apply(lp.gravity, w, h, containerRect, childRect)
                child.layout(childRect.left, childRect.top, childRect.right,
                    childRect.bottom)
                (r1, t1, rh1)
            }
        })
    }
}
