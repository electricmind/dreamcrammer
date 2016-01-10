package ru.wordmetrix.dreamcrammer.db

import java.io._
import java.net._

abstract class Pronunciation {
    def apply(url : String) : Option[String]
}

object PronunciationWiktionary extends Pronunciation {
    def apply(url : String) : Option[String] = ("http://en.wiktionary.org/wiki/%s".format(url.replace(" ","%20")) :+: Page("""<span\s+class="IPA"[^>]*>/([^/]*)/</span>""".r, x=>x.subgroups(0)))
}


object PronunciationDictionary extends Pronunciation {
    def apply(url : String) : Option[String] = {
        ("http://dictionary.reference.com/browse/%s".format(url.replace(" ","%20")) :+: Page("""<span\s+class="prondelim">/</span><span\s+class="pron">(.*)</span><span\s+class="prondelim">/</span>""".r, x=>x.subgroups(0))) match {
             case Some(s) => {
                val s1 = s.split("<span[^>]*>[^<]*</span[^>]*>").mkString("")
                //println("=====>" + s + "   |   " + s1)
                Some("<[^>]+>[^<]+<[^>]+>|<[^>]+/>".r.replaceAllIn(s1,""))
             }
             case None => None
        }
    }

}

object PronunciationCommon extends Pronunciation {
    val root = new File("cache")

    override
    def apply(word : String) : Option[String] = {
        val fn = new File(root, "pronunciation-" + word.replace("/","-") + ".txt")
        try {
            Some(io.Source.fromFile(new File("aa.txt")).getLines.mkString(""))
        } catch {
            case x : Throwable => (PronunciationWiktionary(word) orElse  PronunciationDictionary(word)) match {
                case Some(x) => {
                    val f = new OutputStreamWriter(new FileOutputStream(fn))
                    f.write(x) 
                    f.close()
                    Some(x)
                }
                case None => None
            }
        }
    }
}

/*object pronounciatontest {
  def main(args : Array[String]) = for {
    (track,word) <- args.map( x=> (TrackCommon.apply(x), x)).filter(_._1 != None)
  } {
    val file = new FileOutputStream("%s.mp3".format(word))
    file.write(track.apply)
    file.close()
  }
}*/

object sqlpronunciation {
    def apply(implicit db : DB) = {
        for (
            word <- db.query("""SELECT word_id, word_value FROM word WHERE word_ipa is null and word_frequency > 0 """,
                x => new Word(x.columnInt(0)) { 
                    val _value = x.columnString(1)
                    override lazy val value = _value
                }
             )
        ) {
            PronunciationCommon(word.value) match {
                case Some(x) => word.setIPA(SomeData(x))
                case _ => word.setIPA(NoData)
            }
        }
    }
}

