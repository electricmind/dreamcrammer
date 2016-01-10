package ru.wordmetrix.dreamcrammer

import scala.annotation.tailrec

import android.content.{Context, Intent}
import android.view.{Menu, MenuItem, MenuInflater}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import android.app.SearchManager
import android.widget.SearchView
import android.support.v4.app.FragmentActivity

abstract trait MenuPool extends MenuFieldBase 
{
  def takepicture()

  def doMD5()

  def doClear()

  override
  def menuid = R.menu.pool

  override 
  def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {

      case R.id.takepicture => {
        println("Edit")
        takepicture()
        true
      }

      case R.id.md5 => {
        println("do md5")
        doMD5()
        true
      }


      case R.id.clear => {
        println("do clear")
        doClear()
        true
      }




      case x => {
        super.onOptionsItemSelected(item)
      }
    }
}


