package ru.wordmetrix.dreamcrammer.db
import scala.annotation.tailrec
import java.io._
import java.net._

object sqltrack {
    def apply(implicit db : DB) = {
        println("111")
        for (
            word <- db.query("""SELECT word_id, word_value FROM word WHERE not word_notrack and word_track is null and word_frequency > 0 """,x => new Word(x.columnInt(0)) { 
                var _value = x.columnString(1) 
                override lazy val value = _value
            })
        ) {
            TrackCommon.get(word.value) match {
                case Some(x) => word.setTrack(SomeData(x))
                case None => word.setTrack(NoData)
            }
        }
    }
}
