package ru.wordmetrix.dreamcrammer.db

import android.database.sqlite._
import android.content.Context
import android.database.Cursor
import ru.wordmetrix._
import android.util.LruCache

class SQLiteConnection(val context: Context, val name: String, version: Int, qq: SQLiteAndroid) extends SQLiteOpenHelper(context, name, null, version) {

    def onCreate(db: SQLiteDatabase) = {
        log("Database %s is creating", name)

        qq.schema.split("\\s*;\\s*").map(x => db.execSQL(x))
    }

    var db: SQLiteDatabase = null

    override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
        qq.onUpgrade((x: String) => db.execSQL(x), oldVersion: Int, newVersion: Int)
    }
}

object dbstatic {
    var db: Option[SQLiteConnection] = None
}

class SQLiteAndroid(context: Context, name: String = "taylor.db", debug: Boolean = false) extends DB {
    var version = getTargetVersion()

    var cache: LruCache[String, SQLiteStatement] = new LruCache[String, SQLiteStatement](100) {
        override def create(query: String): SQLiteStatement = db.compileStatement(query)
    }

    val db: SQLiteDatabase = (dbstatic.db match {
        case Some(x) => x
        case None => {
            dbstatic.db = Some(new SQLiteConnection(context, name, getTargetVersion(), this))
            dbstatic.db.get
        }
    }).getWritableDatabase

    def close() = {} //db.close()

    def cursor(query: String, args: Any*) = {
        val st = cache.get(query)
        st.clearBindings()
        for ((arg, num) <- args.zipWithIndex) {
            log("%s %s", num, arg)
            bind(st, num + 1, arg)
        }
        st.execute()
        st
    }

    def query[T](query: String, factory: DB.Access => T, args: Any*) = {
        if (debug) log("%s : %s", query, args)

        val cursor = db.rawQuery(query, args.map(convert).toArray)

        def getall: Stream[T] = if (cursor.moveToNext()) factory(access(cursor)) #:: getall else { cursor.close(); Stream[T]() }

        getall
    }

    def convert(arg: Any) = arg match {
        case x: Array[Byte] => x.toString
        case x: String      => x.toString
        case x: Int         => x.toString
        case x: Double      => x.toString
        case x: Long        => x.toString
        case x: Boolean     => (if (x) 1 else 0).toString
    }

    /*    def query[T](query : String, factory : Access => T, args : Any*) = {
        if (debug) log("%s : %s", query, args)
        val c = cursor(query, args : _ *)
        def getall : Stream[T] = if (c.moveToNext()) factory(access(c)) #:: getall else {  c.close(); Stream[T]() }
        getall
    }*/

    def bind(st: SQLiteStatement, num: Int, arg: Any) = arg match {
        case x: Array[Byte] => st.bindBlob(num, x)
        case x: String      => st.bindString(num, x)
        case x: Int         => st.bindLong(num, x)
        case x: Double      => st.bindDouble(num, x)
        case x: Long        => st.bindLong(num, x)
        case x: Boolean     => st.bindLong(num, if (x) 1 else 0)
    }

    def update(query: String, args: Any*) = {
        if (debug) log("%s : %s", query, args)
        val c = cursor(query, args: _*)
        //c.moveToNext()
        //c.close()
    }

    def access(cursor: Cursor) = new DB.Access {
        def columnInt(num: Int): Int = cursor.getInt(num)
        def columnString(num: Int): String = cursor.getString(num)
        def columnDouble(num: Int): Double = cursor.getDouble(num)
        def columnBlob(num: Int): Array[Byte] = cursor.getBlob(num)
    }
}
