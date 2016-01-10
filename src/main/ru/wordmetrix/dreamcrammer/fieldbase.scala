package ru.wordmetrix.dreamcrammer

import android.app.Activity
import android.os.{Bundle}
import android.view.ViewGroup
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.content.Context
import android.app.SearchManager
import android.view.{View,LayoutInflater}
import android.widget.{ArrayAdapter, TextView, SearchView, ListView}
import android.content.Intent
import android.support.v4.widget.DrawerLayout

abstract
class FieldBase[T <: Field] extends DreamCrammerBase  { 
    def publish(intent : Intent) : Unit

    def publish(field : T) : Unit

    def doFirst(view : View, field : T) : Unit

    def doAddField(view : View, query : String) : Boolean

    def search_query(query : String) : Stream[T] 
  
    def displayItem(itemview : ViewGroup, field : T) : ViewGroup

    var query : Option[String] = None

    def tid(x : String) = "%s".format(x)

    def doReload() = field.map(x => publish(x.refresh().asInstanceOf[T]))

    def doDelete() = ConfirmationDialog(
        "Do you really wanna to delete it?", 
        {
            field.map(_.delete())
            finish()
        }
    ).show(getSupportFragmentManager(), "confirmationdialog")

    override
    def onCreate(savedInstanceState : Bundle) = {
        super.onCreate(savedInstanceState)
        handleIntent(getIntent())
    }

    override
    def onNewIntent(intent : Intent) : Unit = {
        setIntent(intent)
        handleIntent(intent);
    }

    var field : Option[T] = None
    def handleIntent(intent : Intent) : Unit = intent match {
        case x if Intent.ACTION_SEARCH.equals(x.getAction()) => doSearch(x.getStringExtra(SearchManager.QUERY))
        case x => publish(getIntent())
    }

    def doFirst(view : View) : Unit = {
        field match {
            case None => {}
            case Some(field) => doFirst(view, field)
        }
    }

    def doAddField(view : View) : Unit = {
        // ticket : Change logic: new created field should be published and added into search (we might expose a few "similar" word in this, for example - with the same soundex)
        query = query match {
            case None => None
            case Some(query) => {
                doAddField(view, query)
                doSearch(query)
                None
            }
        }
    }

    val layout_item : Int //= R.layout.worditem_left

    def doSearch(q : String) = {
        if (search_query("%s".format(q.trim)) match {
            case Stream() => {
                findViewById(R.id.fields).setVisibility(View.GONE)
                findViewById(R.id.addbutton).setVisibility(View.VISIBLE)
                query = Some(q.trim)
                true
            }
            case field #:: fields => {
                val fieldadapter = new ArrayAdapter(
                    FieldBase.this, android.R.layout.simple_list_item_1, 
                    (field #:: fields).foldLeft(new java.util.ArrayList[T]())((array, x) => { array.add(x); array })) {


                    override 
                    def getView (position : Int, convertView : View, parent : ViewGroup) : View = {
                        val itemview = displayItem(convertView match {
                            case null => FieldBase.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(layout_item, null).asInstanceOf[ViewGroup]
                            case v : ViewGroup => v
                        }, getItem(position))

                        
                        itemview.setOnClickListener(new View.OnClickListener {
                            override
                            def onClick(v : View) {
                                findViewById(R.id.drawer).asInstanceOf[DrawerLayout].closeDrawer(findViewById(R.id.searchview))
                                publish(getItem(position))
                            }
                        })
                        itemview
                    }
                }

                findViewById(R.id.fields).asInstanceOf[ListView].setAdapter(fieldadapter)
                findViewById(R.id.addbutton).setVisibility(View.GONE)
                findViewById(R.id.fields).setVisibility(View.VISIBLE)
                publish(field)
                if (fields.size > 0) true else false
            }
        }) {
            findViewById(R.id.drawer).asInstanceOf[DrawerLayout].openDrawer(findViewById(R.id.searchview))
        } else {
            findViewById(R.id.drawer).asInstanceOf[DrawerLayout].closeDrawer(findViewById(R.id.searchview))
        }
        
    }
}
