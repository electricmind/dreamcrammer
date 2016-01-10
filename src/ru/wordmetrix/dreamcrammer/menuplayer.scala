package ru.wordmetrix.dreamcrammer

import java.net._
import java.io._
import android.app.Activity
import android.os.Bundle
import scala.annotation.tailrec
import scala.util.Random
import android.widget.{TextView,ToggleButton, Button}
import android.view.{View, ActionProvider, LayoutInflater}
import android.content.SharedPreferences
import android.content.Context
import android.view.{Menu, MenuItem, MenuInflater}
import android.content.Intent
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

class PauseResumeButtonActionProvider(val context : MenuPlayer) extends ActionProvider(context) {
    def onCreateActionView() : View = {
        log("create action")
        var layoutInflater : LayoutInflater = LayoutInflater.from(context);
        var view : View = layoutInflater.inflate(R.layout.pauseresume,null);
        context.pauseresumebutton = view.findViewById(R.id.pauseresume).asInstanceOf[ToggleButton]
        return view;
    }
}

class ExitButtonActionProvider(val context : MenuPlayer) extends ActionProvider(context) {
    def onCreateActionView() : View = {
        log("create exit action")
        var layoutInflater : LayoutInflater = LayoutInflater.from(context);
        var view : View = layoutInflater.inflate(R.layout.exit,null);
        context.exitbutton = view.findViewById(R.id.exit).asInstanceOf[Button]
        return view;
    }
}

class StopButtonActionProvider(val context : MenuPlayer) extends ActionProvider(context) {
    def onCreateActionView() : View = {
        log("create exit action")
        var layoutInflater : LayoutInflater = LayoutInflater.from(context);
        var view : View = layoutInflater.inflate(R.layout.stop,null);
        context.stopbutton = view.findViewById(R.id.stop).asInstanceOf[Button]
        return view;
    }
}

abstract 
trait MenuPlayer extends MenuBase
{
  override
  def menuid = R.menu.player

  var pauseresumebutton : ToggleButton = null
  var exitbutton : Button = null
  var stopbutton : Button = null

  override
  def onCreateOptionsMenu(menu : Menu) : Boolean = {
    super.onCreateOptionsMenu(menu)
    menu.findItem(R.id.pauseresume).asInstanceOf[MenuItem].setActionProvider(
       new PauseResumeButtonActionProvider(this)
    )

    menu.findItem(R.id.exit).asInstanceOf[MenuItem].setActionProvider(
       new ExitButtonActionProvider(this)
    )

    menu.findItem(R.id.stop).asInstanceOf[MenuItem].setActionProvider(
       new StopButtonActionProvider(this)
    )

    return true;
  }

  override 
  def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId match {
      case R.id.stop => {
        println("Questionaire")
        doStop(null)
        true
      }

      case R.id.exit => {
        println("Exit")
        doExit(null)
        true
      }

      case x => {
        super.onOptionsItemSelected(item)
      }
    }

    def doPauseResume(view :View)
    def doExit(view :View)
    def doStop(view :View)
}
