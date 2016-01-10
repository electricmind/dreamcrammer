package ru.wordmetrix.dreamcrammer.db

import java.io._
import java.net._

abstract class Track {
    def get(url : String) : Option[Array[Byte]]
}

object TrackCommon extends Track {
    override
    def get(word : String) : Option[Array[Byte]] = TrackWiktionary.get(word) orElse TrackDictionary.get(word)
}

object TrackCommonWithCache extends Track {
    val root = new File("cache")

    override
    def get(word : String) : Option[Array[Byte]] = {
        val fn = new File(root, word.replace("/","-") + ".dat")
        try {
            val f = new FileInputStream(fn)
            val buf = new Array[Byte](f.available)
            f.read(buf)
            f.close()
            println(word + " from cache")
            Some(buf)
        } catch {
            case x => (TrackWiktionary.get(word) orElse TrackDictionary.get(word)) match {
                case Some(x) => {
                    val f = new FileOutputStream(fn)
                    f.write(x) 
                    f.close()
                    Some(x)
                }
                case None => None
            }
        }
    }
}
