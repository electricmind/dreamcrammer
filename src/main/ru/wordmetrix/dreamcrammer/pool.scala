package ru.wordmetrix.dreamcrammer

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import scala.annotation.tailrec

import android.app.{Activity}
import android.view.{View,MotionEvent}
import android.content.{SharedPreferences,Context,Intent,ServiceConnection, ComponentName}
import android.view.{Menu, MenuItem, MenuInflater, ViewGroup, View,LayoutInflater}
import android.widget.{ToggleButton, TextView, ArrayAdapter, ListView, AbsListView, ProgressBar}
import android.os.{Binder, IBinder, Bundle}
import android.support.v4.util.LruCache
import java.util.concurrent._
import android.support.v4.app.{Fragment, ListFragment, FragmentTransaction}
import android.support.v4.app.FragmentActivity
import android.support.v4.content.LocalBroadcastManager
import android.content.{Intent, IntentFilter, BroadcastReceiver}

class TaskList extends ListFragment {
    var mCurCheckPosition : Int = 0;

    def service = getActivity().asInstanceOf[PoolHandler].service

    override
    def onActivityCreated(savedInstanceState : Bundle) : Unit = {
        super.onActivityCreated(savedInstanceState);

        setListAdapter(new TaskListAdapter(getActivity().asInstanceOf[DreamCrammerBase], R.layout.taskitem, service.get.tasks().toList))

        if (savedInstanceState != null) {
            // Restore last state for checked position.
            mCurCheckPosition = savedInstanceState.getInt("curChoice", 0);
        }

        log("onAC: %s", getActivity().findViewById(R.id.task))

        Option(getActivity().findViewById(R.id.task)).map(x => {
            // In dual-pane mode, the list view highlights the selected item.
            getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            // Make sure our UI is in the correct state.
            publish(mCurCheckPosition);
        })
    }

    override
    def onSaveInstanceState(outState : Bundle) : Unit = {
        super.onSaveInstanceState(outState)
        outState.putInt("curChoice", mCurCheckPosition);
    }

    override
    def onListItemClick(l : ListView, v : View, position : Int, id : Long) : Unit = {
        publish(position)
    }


    def publish(position : Int) {
        mCurCheckPosition = position
        getListView().setItemChecked(position, true);

        val task = getListAdapter().getItem(position).asInstanceOf[Task]

        def create(task : Task) = {
            val ft = getFragmentManager().beginTransaction()
            log("create task")
            ft.replace( R.id.task, TaskFragment.newInstance(task) )
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            //ft.addToBackStack(null) // right now back stack provide more headache than pleasure
            ft.commit()
        }

        log("task idS: %s", task.id.get)

        Option(getActivity().findViewById(R.id.task)) match {
            case Some(view) => Option(getActivity().getFragmentManager().findFragmentById(R.id.task).asInstanceOf[TaskFragment]) match {
                case Some(taskfragment) if (taskfragment.id == task.id.get) => {}

                case Some(taskfragment) if (taskfragment.id != task.id.get) =>{ log("fragmentid: %s",taskfragment.id); create(task) }

                case None => { log("not exist"); create(task) }
            }

            case None => startActivity(new Intent() {
                setClass(getActivity(), classOf[TaskActivity])
                putExtra("task", task.id.get)
            })
        }
    }

    override
    def onResume() : Unit =  {
        log("Resume TaskListFragment")
        super.onResume()
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(broadcastreceiver, new IntentFilter( Task.Message.Progress.toString))
    }

    lazy val broadcastreceiver = new BroadcastReceiver() {
        override
        def onReceive(context : Context, intent : Intent) = {
          log("Broadcast recieved about progress %s",  intent.getSerializableExtra("progress"))
          intent.getSerializableExtra("progress") match {
             case TaskAborted(_) | TaskAdopted(_) | ChangeStatus(_,_) => setListAdapter(new TaskListAdapter(getActivity().asInstanceOf[DreamCrammerBase], R.layout.taskitem, service.get.tasks().toList))
             //new TaskDisplay(getActivity().asInstanceOf[DreamCrammerBase], service.get.task(id)).view(getView().asInstanceOf[ViewGroup])

             case x => log("Not recognized (list): %s", x)
          }
        }
    }

    override
    def onPause() : Unit = {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastreceiver);
        super.onPause();
    }



}


object TaskFragment {
    def newInstance(task : Task) = new TaskFragment() {
        setArguments({
            val bundle = new Bundle()
            bundle.putInt("task", task.id.get)
            bundle
        })
    }
}

class TaskFragment extends Fragment {
    def id = getArguments().getInt("task", 0)
    def service = getActivity().asInstanceOf[PoolHandler].service

    override
    def onCreateView( inflater : LayoutInflater, container : ViewGroup , savedInstanceState : Bundle ) = {
        container match {
            case null => null
            case container =>  
                service match {
                    case Some(x) => {
                        log("publish")
                        val td = new TaskDisplay(getActivity().asInstanceOf[DreamCrammerBase], service.get.top(id))
                        val viewgroup = td.view(R.layout.taskview)
                        service.map(x => { log("bind"); td.bind(viewgroup,x) })
                        viewgroup
                    }

                    //publish( service.get.task(id) )
                    // ticket : write special view for lack of service                      
                    case None => inflater.inflate(R.layout.taskview, null).asInstanceOf[ViewGroup]
                }
            
        }
    }

    def publish(task : Task) = {
        log("publish")
        val td = new TaskDisplay(getActivity().asInstanceOf[DreamCrammerBase], task)
        val viewgroup = td.view(R.layout.taskview)
        service.map(x => { log("bind"); td.bind(viewgroup,x) })
        viewgroup
    }


    override
    def onResume() : Unit =  {
        log("Resume TaskFragment")
        super.onResume()
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(broadcastreceiver, new IntentFilter( Task.Message.Progress.toString))
    }

    lazy val broadcastreceiver = new BroadcastReceiver() {
        override
        def onReceive(context : Context, intent : Intent) = {
          log("Broadcast recieved about progress %s",  intent.getSerializableExtra("progress"))
          intent.getSerializableExtra("progress") match {
             case Progress(id1, size, progress) if (id == id1) => {
                 log("Progress: %s %s %s",id, size, progress)
                 val progressbar = getView().findViewById(R.id.progressbar).asInstanceOf[ProgressBar]
                 progressbar.setMax(size)
                 progressbar.setProgress(progress)
             }

             case ChangeStatus(id1, status) if (id == id1) => {
                log("publish")
                val td = new TaskDisplay(getActivity().asInstanceOf[DreamCrammerBase], service.get.top(id))
                val viewgroup = td.view(getView().asInstanceOf[ViewGroup])
                service.map(x => { log("bind"); td.bind(viewgroup,x) })
            }

             case TaskAborted(id1) if (id == id1) => {
                log("on abort")
                val viewgroup = getView().asInstanceOf[ViewGroup]
                service.get.topOption(id) match {
                    case Some(task) => {
                        getArguments().putInt("task", task.id.get)
                        val td = new TaskDisplay(getActivity().asInstanceOf[DreamCrammerBase], task)
                        td.view(viewgroup)
                        service.map(x => { log("bind"); td.bind(viewgroup,x) })
                        
                    }
                    case None => viewgroup.removeAllViews()
                }
            }

             case x : Throwable => log("Not recognized: %s", x)
          }
        }
    }

    override
    def onPause() : Unit = {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastreceiver);
        super.onPause();
    }


}

abstract trait PoolHandler {
    var service : Option[TaskService]
}

class Pool extends DreamCrammerBase with PoolHandler with MenuPool {
    override
    def layout = R.layout.main

    var already = false

    def doDelete(): Unit = {}
    def doReload(): Unit = {}
    def doClear() : Unit = service.map(_.delete())

    def doMD5() = startService(new Intent(this, classOf[TaskService]) {
        putExtra("task", new Task(Task.Kind.MD5,0, "", "",""))
    })

    def doEdit() = {}
    def takepicture = {}


    val serviceconnection : ServiceConnection = new ServiceConnection() {
        override
        def onServiceConnected(className : ComponentName, binder : IBinder ) : Unit = {
            service = Some(binder.asInstanceOf[TaskBinder].getService())
            service.map(x => {
            })
            log("onServiceConnected! %s",service)

            Option(getSupportFragmentManager().findFragmentById(R.id.tasks)).map(fragment => {
                log("fragment has been found")
                getSupportFragmentManager().beginTransaction().remove(fragment).commit()
            })
 
            //try {
            if (!already) { 
               setContentView(R.layout.pool)
               already = true
            }
//            } catch { case _ => {} }
        }

        override
        def onServiceDisconnected(arg0 : ComponentName) : Unit = {
            service = None
        }
    }

    var service : Option[TaskService] = None

    override
    def onStart() : Unit =  {
        super.onStart();
        bindService(new Intent(this, classOf[TaskService]), serviceconnection, Context.BIND_AUTO_CREATE)
        // startService(new Intent(this, classOf[TaskService]) { })
    }

    override
    def onStop() : Unit = {
        super.onStop();
        service.map(x => unbindService(serviceconnection))
        service = None
    }
}

class TaskActivity extends FragmentActivity {

    override
    def onCreate(savedInstanceState : Bundle) : Unit =  {
        super.onCreate(savedInstanceState);

        /*if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            finish();
        }*/ 

        if (savedInstanceState == null) {
            // During initial setup, plug in the details fragment.
            val task : TaskFragment  = new TaskFragment();
            task.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, task).commit();
        }
    }
}
