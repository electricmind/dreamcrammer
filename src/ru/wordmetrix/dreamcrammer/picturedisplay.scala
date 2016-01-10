package ru.wordmetrix.dreamcrammer

import java.io._
import android.content.{Context, Intent}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{Menu, MenuItem, View, ViewGroup, LayoutInflater}
import android.widget.{ToggleButton, TextView, Button, CompoundButton, ImageView, ArrayAdapter, ListView, PopupMenu, AdapterView, GridView}
import android.graphics.{BitmapFactory,Bitmap}
import android.support.v4.util.LruCache

class PictureDisplay(context : DreamCrammerBase, picture : Picture) extends  BaseDisplay(context) {
    override
    def item(resource : Int = R.layout.pictureitem) = super.item(resource)

    override
    def item(viewgroup : ViewGroup) : ViewGroup =  {
        val imageView : ImageView = viewgroup.findViewById(R.id.picture_body).asInstanceOf[ImageView]
        //log("picture %s %s %s", imageView, picture, picture.body.size)
        // ticket : Add a message that something is wrong with picture instead of silent droping it

        picture.bodyOption match {
            case Some(body) => try {
                imageView.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(body,0,picture.body.size),256,256,true) )
                //imageView.setImageBitmap(BitmapFactory.decodeByteArray(body,0,picture.body.size))
            } catch {
                case x : Throwable => log("Bitmap is broken",x)
            }

            case None => log("Bitmap has not been loaded yet")
        }

        
        viewgroup
    }

    override
    def view(resource : Int = R.layout.pictureview) : ViewGroup  = super.view(resource)

        
}
