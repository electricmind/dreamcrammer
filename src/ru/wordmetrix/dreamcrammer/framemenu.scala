package ru.wordmetrix.dreamcrammer
import android.widget.{FrameLayout, LinearLayout, PopupMenu}
import android.content.{Context}
import android.util.AttributeSet
import android.view.{MenuInflater,MenuItem, View}


import android.view.{View,GestureDetector,MotionEvent}
import android.support.v4.view.{MotionEventCompat,GestureDetectorCompat}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._


class FrameMenu(context : Context, attrs : AttributeSet, defStyle : Int) extends FrameLayout(context, attrs, defStyle) {
    log("FrameMenu attrs: %s", attrs)

    var listener : Option[PopupMenu.OnMenuItemClickListener] = Some(new PopupMenu.OnMenuItemClickListener() {
            //override
            def onMenuItemClick(item : MenuItem) : Boolean = item.getItemId() match {
                case R.id.delete => log("Delete item"); false
                case R.id.lookat => log("Look at item"); false
                case _ => log("yawn!"); false
            }
        })

    def setOnMenuItemClickListener(l : PopupMenu.OnMenuItemClickListener) {
        listener = Some(l)
    }

    def this(context : Context, attrs : AttributeSet) = this(context, attrs, 0)

    def this(context : Context) = this(context, null, 0)

    def showPopup(v : View) : Unit = {
        log("showPopup called")
        val popup : PopupMenu = new PopupMenu(context, v)
        listener match {
            case Some(x) => popup.setOnMenuItemClickListener(x)
            case _ => {}
        }

        val  inflater : MenuInflater = popup.getMenuInflater();


        inflater.inflate(R.menu.actions, popup.getMenu())
        popup.show()
    }

/*    setOnClickListener(new View.OnClickListener() {
        override
        def onClick(v : View) : Unit  = showPopup(FrameMenu.this)
    })*/


    setOnLongClickListener(new View.OnLongClickListener() {
        override
        def onLongClick(v : View) : Boolean  = {
            showPopup(FrameMenu.this)
            true
        }
    })

/*
    override
    def onSizeChanged (w : Int, h : Int, oldw : Int, oldh : Int) : Unit = {
        log("onSizeChanged: %s %s %s",w,h,oldw,oldh)
        //setLayoutParams(new LinearLayout.LayoutParams(h, h))
    }*/

}