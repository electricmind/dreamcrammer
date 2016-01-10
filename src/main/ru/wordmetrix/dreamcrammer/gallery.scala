package ru.wordmetrix.dreamcrammer

import android.app.Activity
import android.os.{Bundle}
import android.view.ViewGroup
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.Context
import android.app.SearchManager
import android.view.{View,LayoutInflater}
import android.widget.{ArrayAdapter, TextView, SearchView, ListView, ImageView}
import android.content.Intent
import android.support.v4.widget.DrawerLayout
import android.provider.MediaStore
import android.graphics.Bitmap

// It's my responsiblility to check that hasSystemFeature(PackageManager.FEATURE_CAMERA) and disable feature

class Gallery extends FieldBase[Picture]  with MenuGallery with PictureBaseDialog.Listener[Picture] {

    def doMD5() = field.map(x => startService(new Intent(this, classOf[TaskService]) {
        putExtra("task", new Task(Task.Kind.MD5,x.id, "", "",""))
    }))

    def doFirst(view : View, picture : Picture) = taskasync {
        convertors.word2pictures.ahead(picture)
        convertors.picture2words.ahead(picture)
    } {}

    override
    val layout_item = R.layout.pictureitem_left

    def doEdit() = field.map(new PictureEditDialog(_).show(getSupportFragmentManager(), "pictureedit"))

    def takepicture() = {
        val intent : Intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent,  1888)
    }

    override
    def onActivityResult(requestCode : Int, resultCode : Int, intent : Intent) = if (intent != null) {
        log("%d %d".format(requestCode, resultCode));

        resultCode match {
            case Activity.RESULT_OK => field match {
                case None => {}
                case Some(picture) => { 
                    picture.setBody({
                        val out = new java.io.ByteArrayOutputStream
                        intent.getExtras().get("data").asInstanceOf[Bitmap].compress(
                            Bitmap.CompressFormat.JPEG,
                            90,
                            out
                        )
                        out.toByteArray
                    })
                    publish(picture.refresh())
                }   
            }
            case _ => {}
        }
    }

    def doAddField(view : View, query : String) = {
        new Picture(query)(db)
        true
    }

/*     def addPicture() = picture match {
         case Some(x) => new PictureAddDialog(x).show(getSupportFragmentManager(), "wordadd")
         case None => log("Dialog has been called without any picture")
     }*/

    override
    def layout = R.layout.gallery

    def publish(intent : Intent) = publish(new Picture(intent.getIntExtra("picture_id",1))(db))

    def publish(picture : Picture) = {
        this.field = Some(picture)
        log("Body Op: %s",  picture.bodyOption)
        picture.bodyOption match {
            case Some(body) => new PictureDisplay(this, picture).whole(findViewById(android.R.id.content).asInstanceOf[ViewGroup].getChildAt(0).asInstanceOf[ViewGroup])
            case None => takepicture()
        }
    }

    def search_query(query : String) : Stream[Picture] =  db.query("""select picture_id, picture_value from picture join word_picture using(picture_id) join word using (word_id) where picture_value like ? or word_value like ? group by picture_id order by picture_value limit 30 """, x => new Picture(x.columnInt(0))(db) {
        val _value = x.columnString(1)
        override lazy val value = _value
    },  '%' + query + "%" , query + "%")

    def displayItem(itemview : ViewGroup, picture : Picture) : ViewGroup = 
        new PictureDisplay(Gallery.this, picture).item(itemview)
}
