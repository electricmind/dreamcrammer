package ru.wordmetrix.dreamcrammer.db

import scala.annotation.tailrec
import scala.util.matching.Regex
import java.io._
import java.net._

abstract class Resource

class Page(regex : Regex, select : Regex.Match => String) extends Resource {
    def :+: (url : String) : Option[String] = suck(url) match {
        case Some(x) => regex.findFirstMatchIn(x) match {
            case Some(x) => Some(select(x))
            case None => None
        }
        case None => None
    }

    def :+: (url : Option[String]) : Option[String] = url match {
        case Some(x) => :+:(x)
        case None => None
    }

    def suck(url : String) = try {
       Some(io.Source.fromURL( new URL(url) ).mkString("") )
    } catch {
       case x : java.io.FileNotFoundException => { println("NotFound",x); None }
       case x : Throwable => { println("Word %s: %s".format(url,x)); throw(x); None }
    }
}

object Page {
    def apply(regex : Regex, select : Regex.Match => String) = new Page(regex, select)
}


class Data extends Resource {
    def suck(url : String) : Option[Array[Byte]] = try {
         val st = (new URL(url)).getContent.asInstanceOf[InputStream]
         var count = 0
         Some(Stream.continually({
           val buf = new Array[Byte](1024*1024)
           val size = st.read(buf)
           count = count + size
           println("Read " + size + " " + count)
           (buf.slice(0,size),size)
         }).takeWhile({case (buf,size) =>  -1 != size}).map(_._1).toArray.flatten)
    } catch {
       case x : java.io.FileNotFoundException => { println("NotFound",x); None }
       case x : Throwable => { println("Word %s: %s".format(url,x)); throw(x); None }
    }

    def :+: (url : String) : Option[Array[Byte]] = suck(url)

    def :+: (url : Option[String]) : Option[Array[Byte]] = url match {
        case Some(x) => :+:(x)
        case None => None
    }

}

object Data {
    def apply() = new Data()
}
