package ru.wordmetrix.dreamcrammer

import android.app.Service
import android.content.Intent
import android.os.{AsyncTask, Binder, Handler, IBinder}
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._

class TaskBinder(val service: TaskService) extends Binder {
  def getService(): TaskService = {
    return service
  }
}

class TaskEvaluator(val taskservice: TaskService) extends AsyncTask[AnyRef, Progress, (Task, Boolean)] {

  override
  def onProgressUpdate(progress: Progress*): Unit = progress.map(x => LocalBroadcastManager.getInstance(taskservice).sendBroadcast(new Intent(Task.Message.Progress.toString) {
    putExtra("progress", x.asInstanceOf[Progress])
  }))

  override
  def doInBackground(tasks: AnyRef*): (Task, Boolean) = {
    val task = tasks(0).asInstanceOf[Task]
    log("status: %s", task.status)
    log("kind: %s", task.kind)
    onProgressUpdate(new Progress(task.id.get, 100, 10))
    task match {
      case Task(_, _, _, status, Task.Kind.Track, field, arg1, arg2, arg3) => {
        val word = new Word(field)(taskservice.db)
        log("Start download a track for %s", word.value)
        try {
          word.value.split("\\s+").sortBy(-_.size).toStream.map(x => {
            log("Try ... %s", x)
            TrackCommon.get(x)
          }).flatten.headOption match {
            case Some(x) => word.setTrack(SomeData(x))
            case None => word.setTrack(NoData)
          }
          (task, true)
        } catch {
          case x: Throwable => {
            log("Task %s failed", x, task);
            (task, false)
          }
        }


        /*                TrackCommon.get(word.value.replace(" ","%20")) match {
                            case Some(x) => log("Track has been gotten", word.setTrack(x))
                            case None => (for (value <- word.value.split("\\s+")) yield {
                                log("Try ... %s",value)
                                TrackCommon.get(value)
                            }).flatten match {
                                case xs if (xs.size > 0) => word.setTrack(xs.reduce(_++_))
                                case xs if (xs.size == 0) => word.setNoTrack(true)
                            }
                        }*/
      }

      case Task(_, _, _, status, Task.Kind.Pronunciation, field, arg1, arg2, arg3) => {
        val word = new Word(field)(taskservice.db)
        log("Start download a pronunciation for %s", word.value)
        try {
          PronunciationWiktionary(word.value) orElse PronunciationDictionary(word.value) match {
            case Some(x) => word.setIPA(SomeData(x))
            case None => (for (value <- word.value.split("\\s+")) yield {
              PronunciationWiktionary(value) orElse PronunciationDictionary(value)
            }).flatten match {
              case xs if (xs.size > 0) => word.setIPA(SomeData(xs.mkString(" ")))
              case xs if (xs.size == 0) => word.setIPA(NoData)
            }
          }
          (task, true)
        } catch {
          case x: Throwable => {
            log("Task %s failed", x, task);
            (task, false)
          }
        }
      }

      case Task(Some(id), _, _, status, Task.Kind.Picture, field, arg1, arg2, arg3) => {
        val word = new Word(field)(taskservice.db)
        log("Start download a picture for %s", word.value)
        val md5s = word.pictures.map(_.md5).toSet
        val size = md5s.size + 3
        try {
          for (
            (picture, n) <- ImgGoogle.get(word.value, size).flatten.zipWithIndex
            if !isCancelled()
          ) {
            //isCancelled()
            onProgressUpdate(new Progress(id, size, n))
            if (!md5s.contains(MD5(picture))) {
              log("add picture")
              word.addPicture(picture)
            } else {
              log("picture already exists (task)")
            }
          }
          (task, true)
        } catch {
          case x: Throwable => {
            log("Task %s failed", x, task);
            (task, false)
          }
        }


      }

      case Task(_, _, _, status, Task.Kind.MD5, field, arg1, ag2, arg3) => {
        //taskservice.db.update("UPDATE picture SET picture_md5=''")
        for (picture <- taskservice.db.query("select picture_id, picture_body from picture where picture_md5 = '' or picture_md5 is null", x => new Picture(x.columnInt(0))(taskservice.db) {
          override val body = x.columnBlob(1)
          override val bodyOption = Some(body)
        })) {
          log("Picture %d md5 is: %s", picture.id, picture.md5)
        }
        (task, true)
      }

      case Task(_, _, _, status, _, field, arg1, arg2, arg3) => {
        log("This feature hasn't implemented yet, task: %s", task)
        (task, true)
      }
    }
  }

  override
  def onPostExecute(result: (Task, Boolean)) = result match {
    case (task, true) => taskservice.taskFinished(task); onProgressUpdate(new Progress(task.id.get, 100, 100))
    case (task, false) => taskservice.taskAborted(task)
  }
}

class TaskService extends Service {
  var lock: AnyRef = new Object
  var callbacks = List[Int => Unit]()
  val maxthread = 5
  var nthreads = maxthread - 2
  var task2thread = Map[Int, TaskEvaluator]()

  val handler: Handler = new Handler()

  val binder: IBinder = new TaskBinder(this)

  def tasks() = schedule.items()

  def delete() = {
    schedule.delete()
    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Task.Message.Progress.toString) {
      putExtra("progress", new TaskAdopted(0))
    })
  }

  def tasks(field: Int) = schedule.itemsByField(field)

  def task(id: Int) = schedule.get(id)

  def top(id: Int) = topOption(id).get

  def topOption(id: Int) = schedule.topOption(id)

  def suspend(task: Task) = task.id.map(id => {
    log("try to suspend")
    if (task2thread contains id) {
      log("suspension")
      task2thread(id).cancel(true)
      taskAborted(task)
      task2thread = task2thread - id
    }
  })

  def abort(task: Task) = task.id.map(id => {
    suspend(task)
    schedule.delete(task)
    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Task.Message.Progress.toString) {
      putExtra("progress", new TaskAborted(task.id.get))
    })
  })

  def resume(task: Task) = task.id.map(id => if (!task2thread.contains(task.id.get)) {
    log("Threads : %s", nthreads)
    setStatus(task, Task.Status.Running)
    val te = new TaskEvaluator(this)
    te.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, task)
    task2thread = task2thread + (task.id.get -> te)
    nthreads = nthreads - 1
  })

  override
  def onBind(intent: Intent): IBinder = binder

  def seen(callback: Int => Unit): Unit = {
    callbacks = callback :: callbacks
    println("callbacks :: " + callbacks)
  }

  lazy val db: DB = new SQLiteAndroid(this, "taylor.db", true)
  lazy val schedule = new TaskSchedule()(db)

  override
  def onCreate(): Unit = {
    Toast.makeText(this, "Service has started", Toast.LENGTH_SHORT).show()
    for (
      task <- (schedule.items(Task.Status.Running).take(maxthread) ++ schedule.items(Task.Status.Failed).take(maxthread) ++ schedule.items(Task.Status.Postponed)).take(maxthread)
    ) taskStart(task)
  }

  override
  def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    Toast.makeText(this, "Command has come", Toast.LENGTH_SHORT).show()
    for {
      intent <- Option(intent)
      task <- Option(intent.getSerializableExtra("task")).map(_.asInstanceOf[Task])
    } {
      schedule.add(task) match {
        case Some(task) => {
          log("Scheduled task is %s", task)
          LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Task.Message.Progress.toString) {
            putExtra("progress", new TaskAdopted(task.id.get))
          })
          taskStart(task)
          log("Task started")
        }
        case None => {
          log("Task already scheduled")
        }
      }

      intent.getIntExtra("command", 0) match {
        case 1 => {
          log("Reload failed tasks")
          schedule.items(Task.Status.Failed).map(taskStart)
          log("Tasks reloaded")
        }
        case x => {
          log("Command already scheduled")
          println(x)
        }
      }
    }
    Service.START_STICKY
  }

  override def onDestroy(): Unit = {
    Toast.makeText(this, "Service has done", Toast.LENGTH_SHORT).show();
  }

  def setStatus(task: Task, status: Task.Status.Value) = {
    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Task.Message.Progress.toString) {
      putExtra("progress", new ChangeStatus(task.id.get, status))
    })

    schedule.setStatus(task, status)
  }

  def taskStart(task: Task) = nthreads match {
    case x if (x > 0) => {
      log("Threads : %s", nthreads)
      setStatus(task, Task.Status.Running)
      val te = new TaskEvaluator(this)
      task2thread = task2thread + (task.id.get -> te)
      te.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, task)
      nthreads = nthreads - 1
    }
    case _ => {
      log("Too little threads")
      setStatus(task, Task.Status.Postponed)
    }
  }

  def taskFinished(task: Task) = {
    setStatus(task, Task.Status.Finished)
    log("task %s finished", task)
    task2thread = task2thread - task.id.get

    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Task.Message.Reload.toString) {
      putExtra("kind", task.kind.id)
      putExtra("id", task.field)
    })

    taskNext()
  }

  def taskNext() = {
    nthreads = nthreads + 1
    schedule.headOption match {
      case Some(task) => taskStart(task)
      case None => {}
    }
  }

  def taskAborted(task: Task) = {
    setStatus(task, Task.Status.Failed)
    log("task %s failed", task)
    taskNext()
  }
}
