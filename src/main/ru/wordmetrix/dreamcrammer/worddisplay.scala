package ru.wordmetrix.dreamcrammer

import java.io._
import android.content.{ Context, Intent }

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{ Menu, MenuItem, View, ViewGroup, LayoutInflater }
import android.widget.{ ToggleButton, TextView, Button, CompoundButton, ImageView, ArrayAdapter, ListView, PopupMenu, AdapterView, GridView }
import android.graphics.{ BitmapFactory, Bitmap }
import android.support.v4.util.LruCache

class PhraseListAdapter(context: Context, resource: Int, items: List[Phrase])
        extends ArrayAdapter(context, resource, items.foldLeft(new java.util.ArrayList[Phrase]())((array, x) => { array.add(x); array })) {

    /*    val cache = new LruCache[Int, View](100) {
       override
       def create(position : Int) : View = {
            val itemview = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.phraseitem, null).asInstanceOf[ViewGroup]
       }
    } */

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
        //log("get view at: %s",position)
        val itemview = convertView match {
            case null    => context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.phraseitem, null).asInstanceOf[ViewGroup]
            case v: View => v
        }

        //cache.get(position)
        itemview.findViewById(R.id.phrase_value).asInstanceOf[TextView].setText(getItem(position).value)
        itemview.setOnClickListener(new View.OnClickListener {
            override def onClick(v: View) {
                val item = getItem(position)
                context.startActivity(new Intent(context, classOf[Quotation]) {
                    putExtra("phrase_id", item.id)
                })

            } /*{
                    val item = getItem(position)
                    item.is_pivotal(!item.is_pivotal)

                    PhraseListAdapter.this.remove(item)

                    complimentary match {
                        case None => { log("no comlimentary adpater")}
                        case Some(x) => { log("do co,plimentary"); x.add(item) }
                    }
                }*/
        })

        itemview.setOnLongClickListener(new View.OnLongClickListener {
            override def onLongClick(v: View): Boolean = {
                val item = getItem(position)
                item.is_pivotal(!item.is_pivotal)

                PhraseListAdapter.this.remove(item)

                complimentary match {
                    case None    => { log("no comlimentary adpater") }
                    case Some(x) => { log("do co,plimentary"); x.add(item) }
                }
                true
            }
        })
        itemview

    }

    var complimentary: Option[PhraseListAdapter] = None

    def setComplimentary(adapter: PhraseListAdapter, getBack: Boolean = true) {
        complimentary = Some(adapter)
        if (getBack) {
            adapter.setComplimentary(this, false)
        }
    }
}

/*
   def fill[T](id :Int, viewgroup : ViewGroup, value : => Unit, default : => Unit) = {
       viewgroup.findViewById(id) match {
           case null => default
           case x : T => value
       }
       viewgroup
   }

   fill(R.id.phrase_value, viewgroup, {x.setText}
    

*/

class WordDisplay(context: DreamCrammerBase, word: Word, 
        clickable: Boolean = false) extends BaseDisplay(context) {
    
    override def item(resource: Int = R.layout.worditem) = super.item(resource)

    override def item(viewgroup: ViewGroup): ViewGroup = {
        viewgroup.findViewById(R.id.word_value) match {
            case null => {}
            case x: TextView => {
                x.setText(word.value)
                if (clickable) x.setOnClickListener(new View.OnClickListener {
                    override def onClick(v: View) = {
                        context.startActivity(new Intent(context, classOf[Vocabulary]) {
                            putExtra("word_id", word.id)
                        })
                    }
                })

            }
        }

        /*        viewgroup.findViewById(R.id.word_ipa) match {
            case null => {}
            case x : TextView => word.ipa match {
                case "" => x.setVisibility(View.GONE)
                case ipa => x.setText(ipa)
            }
        }

        viewgroup.findViewById(R.id.word_track) match {
            case null => {}
            case x : View => word.track match {
                case None => x.setVisibility(View.GONE) 
                case Some(track) => x.setOnClickListener(new View.OnClickListener() {
                    override def onClick(view :View) : Unit = context.play(word)
                })
            }
        }

*/
        ((viewgroup.findViewById(R.id.word_track), word.track) match {
            case (null, _) => None
            case (x, null) => { x.setVisibility(View.GONE); None }
            case (button: Button, track) => {
                button.setVisibility(View.VISIBLE)
                button.setOnClickListener(new View.OnClickListener() {
                    override def onClick(view: View): Unit = context.play(word)
                })

                Some(button)
            }
        }) getOrElse viewgroup.findViewById(R.id.word_ipa).asInstanceOf[TextView] match {
            case null => {}
            case label => (word.ipa, label) match {
                case (_, null)                             => {}
                case (NeverData | NoData, label: TextView) => label.setVisibility(View.GONE)
                case (SomeData(ipa), label)                => { label.setVisibility(View.VISIBLE); label.setText(ipa) }
            }
        }

        viewgroup
    }

    override def view(resource: Int = R.layout.wordview) = super.view(resource)

    override def view(viewgroup: ViewGroup): ViewGroup = view(viewgroup, None)

    def view(viewgroup: ViewGroup, complimentary_ : Option[PhraseListAdapter]): ViewGroup = {
        //log("View: %s", viewgroup)
        item(viewgroup)

        viewgroup.findViewById(R.id.word_value_big) match {
            case null        => {}
            case x: TextView => x.setText(word.value)
        }

        viewgroup.findViewById(R.id.word_frequency) match {
            case null        => {}
            case x: TextView => x.setText("%d times".format(word.frequency))
        }

        viewgroup.findViewById(R.id.word_age) match {
            case null        => {}
            case x: TextView => x.setText("%d days".format(word.age))
        }

        viewgroup.findViewById(R.id.word_status) match {
            case null => {}
            case x: TextView => x.setText(word.status match {
                case 3 => "New"
                case 2 => "Learn"
                case 1 => "Known"
                case 0 => "Excluded"
                case _ => "Unknown"

            })
        }

        viewgroup.findViewById(R.id.word_descriptions_frame) match {
            case null => {}
            case x: ViewGroup => word.descriptions match {
                case List() => x.setVisibility(View.GONE)
                case descriptions => {
                    log("find frame d")
                    x.setVisibility(View.VISIBLE)
                    x.findViewById(R.id.word_descriptions).asInstanceOf[TextView].setText(descriptions.map(" - " + _.value).mkString(";\n"))
                }
            }
        }

        viewgroup.findViewById(R.id.word_phrases) match {
            case null        => {}
            case v: TextView => v.setText(word.phrases.map(" - " + _.value).mkString(";\n"))
            case v: ListView => v.setAdapter(new PhraseListAdapter(context, R.layout.phraseitem, word.phrases) {
                log("complimentary %s", complimentary_)
                complimentary_.map(phrases => setComplimentary(phrases))
            })
        }

        viewgroup.findViewById(R.id.word_is_seen).asInstanceOf[ToggleButton] match {
            case null => {}
            case x => {
                x.setChecked(word.is_seen)
                x.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = word.is_seen(isChecked)
                })
            }
        }

        //log("View 1: %s", viewgroup)
        viewgroup
    }

    override def whole(resource: Int = R.layout.wordwhole) = super.whole(resource)

    override def whole(viewgroup: ViewGroup): ViewGroup = {
        val phraselistadapter = viewgroup.findViewById(R.id.word_otherphrases) match {
            case null => None
            case x: ListView => Some({
                val phraselistadapter = new PhraseListAdapter(context, R.layout.phraseitem, word.otherphrases)
                x.setAdapter(phraselistadapter)
                phraselistadapter
            })
        }

        view(viewgroup, phraselistadapter)
        viewgroup.findViewById(R.id.word_pictures) match {
            case null => {}
            case x: GridView => x.setAdapter(new ArrayAdapter(
                context, R.layout.pictureitem,
                word.pictures.foldLeft(new java.util.ArrayList[Picture]())((array, x) => { array.add(x); array })) {

                val cache = new LruCache[Int, View](100) {
                    override def create(position: Int): View = {
                        val pictureview = new PictureDisplay(context, getItem(position)).item(R.layout.pictureitem).asInstanceOf[FrameMenu]
                        pictureview.setOnClickListener(new View.OnClickListener {
                            def onClick(view: View): Unit = {
                                context.startActivity(new Intent(context, classOf[Gallery]) {
                                    putExtra("picture_id", getItem(position).id)
                                })
                            }
                        })
                        pictureview.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener {
                            def onMenuItemClick(item: MenuItem): Boolean = {
                                log("Menu item click %s", item); item.getItemId() match {
                                    case R.id.lookat => {
                                        context.startActivity(new Intent(context, classOf[Gallery]) {
                                            putExtra("picture_id", getItem(position).id)
                                        })
                                        false

                                    }

                                    case R.id.delete => {
                                        log("Delete item")
                                        //record.disable(picture, true)
                                        //pictures.removeView(pictureview)        
                                        false
                                    }

                                    //case R.id.lookat => { log("Look at item"); false }

                                    case _ => log("yawn!"); false
                                }
                            }
                        })
                        pictureview

                    }
                }
                override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
                    /*val itemview = (convertView match {
                        case null => inflate(R.layout.pictureitem)
                        case v : View => v
                    }).asInstanceOf[ViewGroup]*/
                    cache.get(position)
                }
            })
        }

        /*
        viewgroup.findViewById(R.id.word_pictures) match {
            case null => {}
            case x : ListView => {
            x.setOnItemClickListener(new AdapterView.OnItemClickListener {
                def onItemClick(parent : AdapterView[_], view : View, position : Int, id : Long) {
                    log("Item click: %s", position)
                    view.asInstanceOf[FrameMenu].performClick()
                }
            })
            x.setAdapter( new ArrayAdapter(
                context, R.layout.pictureitem, 
                word.pictures.foldLeft(new java.util.ArrayList[Picture]())((array, x) => { array.add(x); array }) ) {

                val cache = new LruCache[Int, View](100) {
                   override
                   def create(position : Int) : View = {
                         val pictureview = new PictureDisplay(context, getItem(position)).item(R.layout.pictureitem).asInstanceOf[FrameMenu]
                         pictureview.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener {
                             def onMenuItemClick(item : MenuItem) : Boolean = { log("Menu item click %s", item); item.getItemId() match {
                                 case R.id.lookat => {
                                     context.startActivity(new Intent(context, classOf[Gallery]) {
                                         putExtra("picture_id", getItem(position).id)
                                     })
                                     false

                                 }


                                 case R.id.delete => { 
                                      log("Delete item")
                                      //record.disable(picture, true)
                                      //pictures.removeView(pictureview)        
                                      false
                                 }

                                  //case R.id.lookat => { log("Look at item"); false }

                                  case _ => log("yawn!"); false
                             }}
                         })
                        pictureview

                   }
                }
                override 
                def getView (position : Int, convertView : View, parent : ViewGroup) : View = {	
                    /*val itemview = (convertView match {
                        case null => inflate(R.layout.pictureitem)
                        case v : View => v
                    }).asInstanceOf[ViewGroup]*/
                    cache.get(position)
                }
            })}
        }
*/
        viewgroup

        /*viewgroup.findViewById(R.id.word_otherphrases_more).asInstanceOf[ToggleButton].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	    override 
            def  onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
                viewgroup.findViewById(R.id.word_otherphrases_frame).setVisibility(
                    if (!isChecked) { 
                    `    View.GONE
                    } else {
                        View.VISIBLE
                    }
                )
            }
        })*/
    }

    override def edit(resource: Int = R.layout.wordview) = super.edit(resource)

    override def edit(viewgroup: ViewGroup): ViewGroup = viewgroup
}