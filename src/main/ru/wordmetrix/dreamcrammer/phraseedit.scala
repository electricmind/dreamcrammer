package ru.wordmetrix.dreamcrammer

import android.app.{Activity, Dialog, AlertDialog}
import android.os.{Bundle}
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.{Context,DialogInterface}
import android.view.{LayoutInflater, ViewGroup}
import android.widget.{EditText}

import android.support.v4.app.DialogFragment

object PhraseBaseDialog {
    trait Listener[T] {
            def publish(t : T) : Unit
    }
}

object PhraseEditDialog {
    trait Listener extends PhraseBaseDialog.Listener[Phrase]
}

object PhraseAddDialog {
    trait Listener extends PhraseBaseDialog.Listener[Word]
}

abstract class PhraseBaseDialog[T] extends DialogFragment {
    var activity :  PhraseBaseDialog.Listener[T] = null

    lazy val viewgroup = setdata(getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.phrasedialog, null).asInstanceOf[ViewGroup])

    override
    def onAttach(activity : Activity) {
        super.onAttach(activity)
        this.activity = activity.asInstanceOf[PhraseBaseDialog.Listener[T]]
    }

    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder

    def setdata(viewgroup : ViewGroup) : ViewGroup = viewgroup

    override
    def onCreateDialog(savedInstanceState : Bundle) : Dialog = {
        log("edit : %s", viewgroup.findViewById(R.id.phrase_value).asInstanceOf[EditText])
        buttons(new AlertDialog.Builder(getActivity())
               .setTitle("Add phrase")
               .setView(viewgroup)).create
    }
}

class PhraseAddDialog(word : Word) extends PhraseBaseDialog[Word] {
    override
    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder =
        dialog.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Add phrase")
                word.addPhrase(viewgroup.findViewById(R.id.phrase_value).asInstanceOf[EditText].getText().toString)
                activity.publish(word.refresh())
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Cancel changes")
            }
        })

}

class PhraseEditDialog(phrase : Phrase) extends PhraseBaseDialog[Phrase] {
    override
    def setdata(viewgroup : ViewGroup) : ViewGroup = {
        viewgroup.findViewById(R.id.phrase_value).asInstanceOf[EditText].setText(phrase.value)
        viewgroup
    }

    override
    def buttons(dialog : AlertDialog.Builder) : AlertDialog.Builder =
        dialog.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Save phrase")
                phrase.setValue(viewgroup.findViewById(R.id.phrase_value).asInstanceOf[EditText].getText().toString)
                activity.publish(phrase.refresh())
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            def onClick(dialog : DialogInterface, id : Int) {
                log("Cancel changes")
            }
        })
}

