package ru.wordmetrix.dreamcrammer
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._
import android.os.Bundle

import android.support.v4.app.DialogFragment
import android.content.{ Context, DialogInterface }

import android.app.{ Activity, Dialog, AlertDialog }

class ConfirmationDialog(question: String, doOk: => Unit, ok: String = "Ok", cancel: String = "Cancel") extends DialogFragment {
    override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        new AlertDialog.Builder(getActivity())
            .setTitle(question)
            .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Agree")
                    doOk
                }
            })
            .setNegativeButton(cancel, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, id: Int) {
                    log("Cancel changes")
                }
            }).create()
    }
}

object ConfirmationDialog {
    def apply(question: String, doOk: => Unit, ok: String = "Ok", cancel: String = "Cancel") = new ConfirmationDialog(question, doOk, ok, cancel)
}

