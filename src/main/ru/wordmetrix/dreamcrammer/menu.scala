package ru.wordmetrix.dreamcrammer

import java.net._
import java.io._
import android.app.Activity
import android.os.Bundle
import scala.annotation.tailrec
import scala.util.Random
import android.widget.{TextView,ToggleButton}
import android.view.View
import android.content.{SharedPreferences,Context,ComponentName}
import android.view.{Menu, MenuItem, MenuInflater}
import android.content.Intent



import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import android.app.SearchManager
import android.widget.SearchView

abstract 
trait MenuBase extends Activity
{
    def menuid = R.menu.menu

    def doReload()

    def doDelete()

    def doRestart() = startService(new Intent(this, classOf[TaskService]) {
        putExtra("command", Task.Command.Reload.id)
    })

    def doPlayer() = {
        startActivity(new Intent(this, classOf[Player]) {
            putExtra("word_ids", Array[Int]() )
        })
    }

    def doPool() = {
        startActivity(new Intent(this, classOf[Pool]) {
        })
    }

    override
    def onCreateOptionsMenu(menu : Menu) : Boolean = {
        val inflater : MenuInflater = getMenuInflater()
        inflater.inflate(menuid, menu)
        true
    }

    override 
    def onOptionsItemSelected(item: MenuItem): Boolean =
        item.getItemId match {
            case R.id.delete => {
                println("Delete!")
               
                //ticket : Add confirmation dialog 
                doDelete()
                true
            }

            case R.id.play => {
                println("Player!")
                doPlayer()
                true
            }

            case R.id.help => {
                println("Help!!")
                true
            }

            case R.id.reload => {
                 println("Start reload!")
                 doReload()
                 true
            }

            case R.id.restart => {
                 println("Start reload!")
                 doRestart()
                 true
            }

            case R.id.pool => {
                 println("Start reload!")
                 doPool()
                 true
            }

            case R.id.settings => {
                 println("Settings!")
                 startActivity(new Intent(this, classOf[Settings]))
                 true
            }

            case x => {
                 println("Nobody knows: " + x)
                 true
            }
    }
}

//    startActivity(new Intent(this, classOf[Reload]))
/*      log("Copy data base from /sdcard/tmp/taylor,db")

      val fin = new FileInputStream(new File("/sdcard/tmp/taylor.db"))
      log("Database size %s", fin.available)
      val data = new Array[Byte](fin.available)
      log("Database data read: %s", fin.read(data))
      fin.close()

      val db = new SQLiteAndroid(this,"taylor.db").db
      val name = db.getPath()
      db.close()
      
      log("Database path %s", that name)
      val fout = new FileOutputStream(name)
      fout.write(data)
      fout.close()
      log("Database copying has been finished") */

