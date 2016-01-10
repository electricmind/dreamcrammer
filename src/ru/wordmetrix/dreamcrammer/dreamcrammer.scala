package ru.wordmetrix.dreamcrammer

import java.io._
import android.app.Activity
import android.content.Context
import android.os.{Bundle}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{View, ViewGroup, LayoutInflater}
import android.widget.{ToggleButton, TextView, CompoundButton, ImageView, ArrayAdapter, ListView}
import android.graphics.{BitmapFactory,Bitmap}
import android.support.v4.app.FragmentActivity

abstract 
class DreamCrammerBase extends FragmentActivity with MenuBase with PlayerBase {

    def layout : Int
    lazy val preferences = new PreferencesBase("DreamCrammer", this)
    lazy val db : DB = new SQLiteAndroid(this, "taylor.db", true)
    lazy val convertors = new Convertors()(db)
    import convertors._

    override
    def onStop() = {
        super.onStop()
        db.close()
    }

    override
    def onCreate(savedInstanceState : Bundle)
    {
        super.onCreate(savedInstanceState)
        setContentView(layout)
    }

   def display(viewGroup : ViewGroup, picture : Picture) = {
       val imageView : ImageView = viewGroup.findViewById(R.id.picture_body).asInstanceOf[ImageView]
       
       picture.bodyOption.map(body =>
           imageView.setImageBitmap( BitmapFactory.decodeByteArray(body,0,body.size) )
       )

       viewGroup
   }


   def display(viewGroup : ViewGroup, word : Word) = {
        log("display %s %s",viewGroup, word)
        new WordDisplay(this, word).view(viewGroup)
        viewGroup.findViewById(R.id.word_is_seen).asInstanceOf[ToggleButton].setChecked(word.is_seen)
        viewGroup.findViewById(R.id.word_is_seen).asInstanceOf[ToggleButton].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            def onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) = word.is_seen(isChecked)
        })

        viewGroup
    }


}

