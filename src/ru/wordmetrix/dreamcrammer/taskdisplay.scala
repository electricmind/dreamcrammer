package ru.wordmetrix.dreamcrammer

import java.io._
import android.content.{Context, Intent}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{Menu, MenuItem, View, ViewGroup, LayoutInflater}
import android.widget.{ToggleButton, TextView, Button, CompoundButton, ImageView, ArrayAdapter, ListView, PopupMenu, AdapterView, GridView, ProgressBar}
import android.graphics.{BitmapFactory,Bitmap}
import android.support.v4.util.LruCache

class TaskListAdapter(context : DreamCrammerBase, resource : Int, items : List[Task]) extends ArrayAdapter(context, resource, items.foldLeft(new java.util.ArrayList[Task]())((array, x) => { array.add(x); array })) {
    override 
    def getView (position : Int, convertView : View, parent : ViewGroup) : View = {	
       new TaskDisplay(context, getItem(position)).item(
           Option(convertView).getOrElse(context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.taskitem, null)).asInstanceOf[ViewGroup]
       )
    }
}

class TaskDisplay(context : DreamCrammerBase, task : Task) extends  BaseDisplay(context) {
    override
    def item(resource : Int = R.layout.taskitem) = super.item(resource)

    override
    def item(viewgroup : ViewGroup) : ViewGroup =  {
        viewgroup.findViewById(R.id.task_kind).asInstanceOf[TextView].setText(task.kind match {
            case Task.Kind.Track => "Download Track"
            case Task.Kind.Pronunciation => "Download pronunciation"
            case Task.Kind.Picture => "Dowload pictures"
            case Task.Kind.MD5 => "Compure MD for pictures"
            case _ => "Unknown"
        })

        viewgroup.findViewById(R.id.task_status).asInstanceOf[TextView].setText(task.status match {
            case Task.Status.Finished => "Finished"
            case Task.Status.Running => "Running..."
            case Task.Status.Postponed => "Postponed"
            case Task.Status.Failed => "Failed"
            case _ => "Unknown"
        })


        viewgroup.findViewById(R.id.task_id).asInstanceOf[TextView].setText(task.kind match {
            case Task.Kind.Track | Task.Kind.Pronunciation | Task.Kind.Picture => new Word(task.field)(context.db).value
            case Task.Kind.MD5 => "Everything" 
            case _ => "Unknown"
        })

        viewgroup
    }

    override
    def view(resource : Int = R.layout.taskview) : ViewGroup  = super.view(resource)

    override
    def view(viewgroup : ViewGroup) : ViewGroup =  {
        Option(viewgroup.findViewById(R.id.progressbar).asInstanceOf[ProgressBar]).map(x => {
            x.setVisibility(View.VISIBLE)
            x.setProgress(task.status match {
                case Task.Status.Running => 0
                case Task.Status.Finished => 100
                case _ => 0
            })
            x.setMax(100)
        })

        Option(viewgroup.findViewById(R.id.task_date).asInstanceOf[TextView]).map(x => {
            x.setText(new java.util.Date(task.date*1000l).toString)
        })

        Option(viewgroup.findViewById(R.id.task_last).asInstanceOf[TextView]).map(x => {
            x.setText(new java.util.Date(task.last*1000l).toString)
        })

        //ticket: Bind for button abort

        item(viewgroup)   
    }

    def bind(viewgroup : ViewGroup, server : TaskService) = {
        val resume = viewgroup.findViewById(R.id.resume)
        val suspend = viewgroup.findViewById(R.id.suspend)
        val abort = viewgroup.findViewById(R.id.abort)
        val enter = viewgroup.findViewById(R.id.enter)

        task.status match {
            case Task.Status.Finished | Task.Status.Postponed | Task.Status.Failed => {
                 suspend.setVisibility(View.GONE)
                 resume.setVisibility(View.VISIBLE)
            }

            case Task.Status.Running => {
                 suspend.setVisibility(View.VISIBLE)
                 resume.setVisibility(View.GONE)
            }

            case _ => {
                 suspend.setVisibility(View.VISIBLE)
                 resume.setVisibility(View.GONE)
            }
        }

        resume.setOnClickListener(new View.OnClickListener {
            def onClick(view : View) : Unit = server.resume(task)
        })

        suspend.setOnClickListener(new View.OnClickListener {
            def onClick(view : View) : Unit = server.suspend(task)
        })

        abort.setOnClickListener(new View.OnClickListener {
            def onClick(view : View) : Unit = server.abort(task)
        })

        task.kind match {
            case Task.Kind.Track | Task.Kind.Pronunciation | Task.Kind.Picture => {
                 enter.setVisibility(View.VISIBLE)
                 //new Word(task.field)(context.db).value
                 enter.setOnClickListener(new View.OnClickListener {
                     def onClick(view : View) : Unit = context.startActivity(new Intent(context, classOf[Vocabulary]) {
                         putExtra("word_id", task.field)
                     })
                 })
            }
            case Task.Kind.MD5 => enter.setVisibility(View.GONE)
            case _ => enter.setVisibility(View.GONE)
        }


        viewgroup
    }
}
