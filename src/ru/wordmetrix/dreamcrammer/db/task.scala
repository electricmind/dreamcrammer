package ru.wordmetrix.dreamcrammer.db
import ru.wordmetrix._

abstract trait TaskId {
    val id : Option[Int]
}
                                                                                                                                 
case class Task(id : Option[Int], date : Int, last : Int, status : Task.Status.Value, kind : Task.Kind.Value, field : Int, arg1 : String, arg2 : String, arg3 : String) extends Serializable with TaskId {
    def this(kind : Task.Kind.Value, field : Int, arg1 : String, arg2 : String, arg3 : String) = this(None, 0, 0, Task.Status.Postponed, kind, field, arg1, arg2, arg3)
}


abstract class Announce(val id : Int) extends Serializable 
case class Progress(override val id : Int, val size : Int, val progress : Int) extends Announce(id) 
case class ChangeStatus(override val id : Int, val status : Task.Status.Value) extends Announce(id)
case class TaskAdopted(override val id : Int) extends Announce(id)
case class TaskAborted(override val id : Int) extends Announce(id)

object Task {
    object Status extends Enumeration {
        type Status = Value
        val Unkonwn = Value(0)
        val Finished = Value(1)
        val Running = Value(2) 
        val Postponed = Value(3) 
        val Failed = Value(4) 
    }

    object Kind extends Enumeration {
        type Kind = Value
        val Track = Value(1)
        val Picture = Value(2) 
        val Pronunciation = Value(3) 
        val Phrase = Value(4) 
        val MD5 = Value(5) 
    }

    object Command extends Enumeration {
        type Command = Value
        val Reload = Value(1)
        val Clear = Value(2)
    }

    object Message extends Enumeration {
        type Message = Value
        val Reload = Value("Reload")
        var Progress = Value("Progress")
    }

    def apply(row : DB.Access) = new Task(
	Some(row.columnInt(0)),
        row.columnInt(1),
        row.columnInt(2),
        Status(row.columnInt(3)),
        Kind(row.columnInt(4)),
        row.columnInt(5),
        row.columnString(6),
        row.columnString(7),
        row.columnString(8)
   )
}

class TaskSchedule(implicit db : DB) {

    def items(status : Task.Status.Value) : Stream[Task] = items(status.id)
    def items(status : Int) : Stream[Task] = status match {
        case status if (status == Task.Status.Failed.id) => db.query(
                """
                    SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
                    FROM task
                    WHERE task_status = ? and ( strftime('%s','now') - task_last ) > 15
                    ORDER BY -task_date
                """, x=>Task(x), status
            )
        case status => db.query(
                """
                    SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
                    FROM task
                    WHERE task_status = ?
                    ORDER BY -task_date
                """, x=>Task(x), status
            )
    }

    def delete() = db.update("delete from task where task_status != 2")

    def delete(task : Task) = task.id.map(id => db.update("delete from task where task_status != 2 and task_id = ?", id))

    def itemsByField(field : Int) = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            WHERE task_field = ?
            ORDER BY -task_date
        """, x=>Task(x), field
    )

    def headOption = items(Task.Status.Postponed).headOption

    def items() = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            ORDER BY -task_date
        """,  x=>Task(x)
    )

    def get(id : Int) = getOption(id).get

    def getOption(id : Int) = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            WHERE task_id = ?
            ORDER BY task_date
        """,  x=>Task(x), id
    ).headOption

    def topOption(id : Int) : Option[Task] = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            WHERE task_id <= ?
            ORDER BY -task_id
        """,  x=>Task(x), id
    ).headOption orElse db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            WHERE task_id >= ?
            ORDER BY -task_id
        """,  x=>Task(x), id
    ).headOption

    def top(id : Int) : Task = topOption(id).get
    def top() : Task = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            ORDER BY -task_id
        """,  x=>Task(x)
    ).head

    def add(task : Task) = db.query(
        """
            SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
            FROM task
            WHERE task_field = ? and task_status != 1 and task_kind = ?
        """,  x=>Task(x), task.field, task.kind.id
    ).headOption match {
        case Some(x) => None
        case None => {
            db.update(
                """
                    INSERT INTO task 
                        (task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3)
                    VALUES(strftime('%s','now'), strftime('%s','now'), ?, ?, ?, ?, ?, ?)
                """,
                task.status.id, task.kind.id,  task.field, task.arg1, task.arg2, task.arg3
            )

            db.query(
                """
                    SELECT task_id, task_date, task_last, task_status, task_kind, task_field, task_arg1, task_arg2, task_arg3
                    FROM task
                    WHERE task_id = (select seq from sqlite_sequence where name='task')
                """,  x=>Task(x)
            ).headOption
        }
    }
 
    def setStatus(task : Task, status : Task.Status.Value) = {
        db.update(
            """
                UPDATE task
                SET task_status = ?, task_last = strftime('%s','now')
                WHERE task_id = ?
            """, status.id, task.id.get
        )
        this
    }

}

