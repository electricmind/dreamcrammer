package ru.wordmetrix.dreamcrammer

import scala.annotation.tailrec

import android.content.{Context, Intent}
import android.view.{Menu, MenuItem, MenuInflater}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

import android.app.SearchManager
import android.widget.SearchView

abstract trait MenuFieldBase extends MenuBase {
    def doEdit()

    override 
    def onOptionsItemSelected(item: MenuItem): Boolean = {
        item.getItemId match {
            case R.id.edit => {
                println("Edit")
                doEdit()
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
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()))
        searchView.setIconifiedByDefault(false)

        true
    }
}



