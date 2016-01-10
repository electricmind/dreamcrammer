package ru.wordmetrix.dreamcrammer

import scala.annotation.tailrec

import android.content.{Context, Intent}
import android.view.{Menu, MenuItem, MenuInflater}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import android.app.SearchManager
import android.widget.SearchView
import android.support.v4.app.FragmentActivity

abstract trait MenuVocabulary extends MenuFieldBase 
{
  def addDescription()

  def addPhrase()

  def addPicture()

  def downloadTrack()

  def downloadPronunciation()

  def downloadPicture()

  override
  def menuid = R.menu.vocabulary

  override 
  def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId match {
      case R.id.addescription => {
        println("Edit")
        addDescription()
        true
      }

      case R.id.addphrase => {
        println("Edit")
        addPhrase()
        true
      }

      case R.id.addpicture => {
        println("Edit")
        addPicture()
        true
      }

      case R.id.downloadtrack => {
        log("A request to download a track is gonna be issue")
        downloadTrack()
        true
      }

      case R.id.downloadpronunciation => {
        println("Edit")
        downloadPronunciation()
        true
      }

      case R.id.downloadpicture => {
        println("Edit")
        downloadPicture()
        true
      }

      case x => {
        super.onOptionsItemSelected(item)
      }
    }


}


