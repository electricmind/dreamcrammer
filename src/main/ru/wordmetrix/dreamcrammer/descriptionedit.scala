package ru.wordmetrix.dreamcrammer


import android.app.{ Activity, Dialog, AlertDialog }
import android.os.{ Bundle }
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.{ Context, DialogInterface }
import android.view.{ LayoutInflater, ViewGroup }
import android.widget.{ EditText }
import android.support.v4.app.DialogFragment


object DescriptionAddDialog {
    abstract trait Listener {
        def publish(word: Word): Unit
    }
}

class DescriptionAddDialog(word: Word) extends DialogFragment {
    var activity: DescriptionAddDialog.Listener = null

     override def onAttach(activity: Activity) {
        super.onAttach(activity)
        this.activity = activity.asInstanceOf[DescriptionAddDialog.Listener]
    }

    override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        val viewgroup = getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.descriptiondialog, null).asInstanceOf[ViewGroup]
        log("edit : %s", viewgroup.findViewById(R.id.description_value).asInstanceOf[EditText])

        new AlertDialog.Builder(getActivity())
            .setTitle("Add description")
            .setView(viewgroup)
            .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Add description")
                    word.addDescription(viewgroup.findViewById(R.id.description_value).asInstanceOf[EditText].getText().toString)
                    activity.publish(word.refresh())
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Cancel changes")
                }
            }).create()
    }

}
