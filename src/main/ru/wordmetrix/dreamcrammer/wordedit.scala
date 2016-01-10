package ru.wordmetrix.dreamcrammer

import android.app.{ Activity, Dialog, AlertDialog }
import android.os.{ Bundle }
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.{ Context, DialogInterface }
import android.view.{ LayoutInflater, ViewGroup }
import android.widget.{ EditText }

import android.support.v4.app.DialogFragment

object WordEditDialog {
    abstract trait Listener {
        def publish(word: Word): Unit
    }
}

class WordEditDialog(word: Word) extends DialogFragment {

    var activity: WordEditDialog.Listener = null

    override def onAttach(activity: Activity) {
        super.onAttach(activity)
        this.activity = activity.asInstanceOf[WordEditDialog.Listener]
    }

    override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        val viewgroup = getActivity().
            getSystemService(Context.LAYOUT_INFLATER_SERVICE).
            asInstanceOf[LayoutInflater].
            inflate(R.layout.worddialog, null).asInstanceOf[ViewGroup]

        log("edit : %s",
            viewgroup.findViewById(R.id.word_value).asInstanceOf[EditText])

        viewgroup.findViewById(R.id.word_value).
            asInstanceOf[EditText].setText(word.value)

        word.ipa.map(
            viewgroup.findViewById(R.id.word_ipa).asInstanceOf[EditText]
                .setText(_))
        //viewgroup.findViewById(R.id.word_frequency).asInstanceOf[EditText].setText(word.frequency.toString)

        var status = word.status
        log("status : %s", status)

        new AlertDialog.Builder(getActivity())
            .setTitle("Edit word")
            .setView(viewgroup)
            .setSingleChoiceItems(Array("Excluded", "Well-known", "Learned", "New").asInstanceOf[Array[CharSequence]], status, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Choice")
                    status = id
                }
            })
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Save word")
                    word.setValue(viewgroup.findViewById(R.id.word_value).asInstanceOf[EditText].getText().toString)
                    word.setIPA(SomeData(viewgroup.findViewById(R.id.word_ipa).asInstanceOf[EditText].getText().toString))
                    word.setStatus(status)
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
