package ru.wordmetrix.dreamcrammer

import android.app.{Activity, Dialog, AlertDialog}
import android.os.{Bundle}
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.{Context,DialogInterface, Intent}
import android.view.{LayoutInflater, ViewGroup}
import android.widget.{EditText}

import android.support.v4.app.DialogFragment

object PictureBaseDialog {
    trait Listener[T] {
            def publish(t : T) : Unit
    }
}

object PictureEditDialog {
    trait Listener extends PictureBaseDialog.Listener[Picture]
}

object PictureAddDialog {
    trait Listener extends PictureBaseDialog.Listener[Word]
}

abstract class PictureBaseDialog[T] extends DialogFragment {
    var activity :  PictureBaseDialog.Listener[T] = null
    var context : Activity = null

    lazy val viewgroup = setdata(getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.picturedialog, null).asInstanceOf[ViewGroup])

    override
    def onAttach(activity : Activity) {
        super.onAttach(activity)
        this.activity = activity.asInstanceOf[PictureBaseDialog.Listener[T]]
        this.context = activity
    }

    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder

    def setdata(viewgroup : ViewGroup) : ViewGroup = viewgroup

    override
    def onCreateDialog(savedInstanceState : Bundle) : Dialog = {
        log("edit : %s", viewgroup.findViewById(R.id.picture_value).asInstanceOf[EditText])
        buttons(new AlertDialog.Builder(getActivity())
               .setTitle("Add picture")
               .setView(viewgroup)).create
    }
}

class PictureAddDialog(word : Word) extends PictureBaseDialog[Word] {
    override
    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder =
        dialog.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Add picture")
                context.startActivity(new Intent(context, classOf[Gallery]) {
                    putExtra("picture_id", word.addPicture(viewgroup.findViewById(R.id.picture_value).asInstanceOf[EditText].getText().toString).id)
                })
                activity.publish(word.refresh())


            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Cancel changes")
            }
        })

}

class PictureEditDialog(picture : Picture) extends PictureBaseDialog[Picture] {
    override
    def setdata(viewgroup : ViewGroup) : ViewGroup = {
        viewgroup.findViewById(R.id.picture_value).asInstanceOf[EditText].setText(picture.value)
        viewgroup
    }

    override
    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder =
        dialog.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Save picture")
                picture.setValue(viewgroup.findViewById(R.id.picture_value).asInstanceOf[EditText].getText().toString)
                activity.publish(picture.refresh())
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Cancel changes")
            }
        })
}

