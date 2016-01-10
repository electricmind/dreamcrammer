package ru.wordmetrix.dreamcrammer

import scala.annotation.tailrec

import android.content.{Context, Intent}
import android.view.{Menu, MenuItem, MenuInflater}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import android.app.SearchManager
import android.widget.SearchView
import android.support.v4.app.FragmentActivity

abstract trait MenuQuotation extends MenuFieldBase 
{
  override
  def menuid = R.menu.quotation

  override 
  def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId match {
      case x => {
        super.onOptionsItemSelected(item)
      }
    }
}


