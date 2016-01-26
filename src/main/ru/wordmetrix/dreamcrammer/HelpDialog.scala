package ru.wordmetrix.dreamcrammer

/**
 * Created by cray on 1/26/16.
 */

import java.util.Date

import android.app.{Activity, Dialog, AlertDialog}
import android.os.{Bundle}
import android.text.format.DateFormat
import ru.wordmetrix.dreamcrammer.db.Phrase
import ru.wordmetrix.dreamcrammer.db.Word
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.{Context,DialogInterface}
import android.view.{LayoutInflater, ViewGroup}
import android.widget.{TextView, EditText}

import android.support.v4.app.DialogFragment
import ru.wordmetrix.log

class HelpDialog() extends DialogFragment {

  lazy val viewgroup = getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.helpdialog, null).asInstanceOf[ViewGroup]

  override  def onAttach(activity : Activity) {
    super.onAttach(activity)
    //this.activity = activity.asInstanceOf[PhraseBaseDialog.Listener[T]]
  }

  override  def onCreateDialog(savedInstanceState : Bundle) : Dialog = {
    viewgroup.findViewById(R.id.help_branch).asInstanceOf[TextView].setText{
      s"Branch: ${Version.branch}"
    }
    viewgroup.findViewById(R.id.help_commit).asInstanceOf[TextView].setText{
      s"Commit: ${Version.commit}"
    }

    viewgroup.findViewById(R.id.help_date).asInstanceOf[TextView].setText{
      s"Date: ${DateFormat.getTimeFormat(this.getActivity).format(new Date(Version.date.toLong))}"
    }

    viewgroup.findViewById(R.id.help_age).asInstanceOf[TextView].setText{
      s"The application is ${(System.currentTimeMillis() - Version.date.toLong) / 1000 / 60 / 60 / 24} days old"
    }

    new AlertDialog.Builder(getActivity())
      .setTitle("Help and About")
      .setView(viewgroup)
      .setNegativeButton("Back", new DialogInterface.OnClickListener() {
        def onClick(dialog : DialogInterface, id : Int) {
          log("Cancel changes")
        }
      }).create
  }
}



