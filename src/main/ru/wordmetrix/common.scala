package ru.wordmetrix

object log {
    val name = "dreamcrammer"
    def apply(s: String, arg: Any*) =
        printf("?V-%s-%s\n", name, s.format(arg: _*))
        
    def apply(s: String, x: Throwable, arg: Any*) =
        printf("?W-%s-(%s)-%s\n", name, x, s.format(arg: _*))
        
    def apply(x: Throwable) = printf("?W-%s-(%s)", name, x)
}

object stack {
    def apply(code: => Unit): Unit = new Thread(null, new Runnable() {
        def run() = code
    }, "InitDataThread", 8 * 1048570) {
        start()
        join()
    }
}

object async {
    def apply(code: => Unit): Unit = new Thread(null, new Runnable() {
        def run() = code
    }, "InitDataThread", 8 * 1048570) {
        start()
    }
}

class Exec[T](val x: T) {
    def exec(fs: T => Unit*) = { for (f <- fs) f(x); }
    def apply(fs: T => Unit*) = { for (f <- fs) f(x); this }
    def get = x
}

object Exec {
    def apply[T](x: T) = new Exec(x)
}


import android.os.{ Binder,  IBinder, AsyncTask, Handler, HandlerThread, Process, Message }

object taskasync {
    def apply[T](background: => T)(post: => Unit = {}) = {
        new AsyncTask[AnyRef, Unit, T] {
            override def doInBackground(tasks: AnyRef*): T = background

            override def onPostExecute(task: T) = post
        }.execute()
    }
}
