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
trait MenuQuestionaire extends MenuBase
{
    override
    def menuid = R.menu.questionaire

    // ticket : imlement turn over queistionnaire
    def doTurnOver()

    def doPostpone()

    override 
    def onOptionsItemSelected(item: MenuItem): Boolean = {
        item.getItemId match {
            case R.id.postpone => {
                log("The item will be postponed")
                doPostpone()
                true
            }

            case R.id.turn => {
                println("Turn over sample!")
                doTurnOver()
                true
            }

            case x => {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override
    def onCreateOptionsMenu(menu : Menu) : Boolean = {
        super.onCreateOptionsMenu(menu)

        val searchManager : SearchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
        val searchView : SearchView = menu.findItem(R.id.search).getActionView().asInstanceOf[SearchView]

        log("component: %s", getComponentName())
        log("info: %s", searchManager.getSearchableInfo(getComponentName()))
        log("searchView: %s", searchView)
        searchView.setSearchableInfo(searchManager.getSearchableInfo( new ComponentName(this, classOf[Vocabulary])))
        searchView.setIconifiedByDefault(false)

        true
    }


}


