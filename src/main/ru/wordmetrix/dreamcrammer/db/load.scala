package ru.wordmetrix.dreamcrammer.db
import io.Source

class Load(db : DB) {
    type Word = String

    def stopword(x : Word) = true //! Set("a", "the", "will", "am", "is", "are", "were","was", "have", "has", "had", "do", "does","gonna", "wanna", "want", "going", "i", "you", "he", she", "it","'", "me","my","mine","her","his","him","there","what","where","who","that").contains(x)

    def parsePhrase(x : String) : List[String] = List("","") ++ x.toLowerCase.split("\\b").map(
         x=>x.replaceAll("\\s+","")
    ).filter(_!="").map(
        x => "^\\p{Punct}+$".r.findFirstMatchIn(x) match { 
            case None => List(x); 
            case _ => x.split("").toList 
        }
    ).flatten.filter(_!="").filter(stopword)

    val rPhrase = """((?<=\.\s{0,4})|^)\p{Lu}+[\p{L}\s,.’'\-]+?\p{Ll}{2}[?!.]($|(?=(\s*\p{Lu})))""".r //"""(?<=\.\s{0,4})\p{Lu}+[\p{L}\s,.\-]+?\p{Ll}{2}[?!.](?=(\s*\p{Lu}))""".r

    def phraseNWords(s : String) : Stream[(String, List[String])] = { println(s); rPhrase.findAllIn("\\s+".r.replaceAllIn(s, " ")).toStream.map(x => (x,parsePhrase(x)))}

    def insertWord(word : Word) = db.query("""SELECT word_id FROM word WHERE word_value=?""",x => x.columnInt(0), word) match {
         case Stream() => db.update("""INSERT into word (word_value) VALUES(?)""",word); db.query("select seq from  sqlite_sequence where name = 'word'", x => x.columnInt(0));
         case x => x
    }

    def insertWords(words : scala.collection.immutable.LinearSeq[Word]) = words.map(insertWord).flatten

    def insertTag(tag : Word) = db.query("""SELECT tag_id FROM tag WHERE tag_value=?""",x => x.columnInt(0), tag) match {
         case Stream() => db.update("""INSERT into tag (tag_value) VALUES(?)""",tag); db.query("select seq from  sqlite_sequence where name = 'tag'", x => x.columnInt(0));
         case x => x
    }

    def insertTags(tags : List[Word]) = tags.map(insertTag).flatten

    def insertDescription(description : Word) = db.query("""SELECT description_id FROM description WHERE description_value=?""",x => x.columnInt(0), description) match {
         case Stream() => db.update("""INSERT into description (description_value) VALUES(?)""",description); db.query("select seq from  sqlite_sequence where name = 'description'", x => x.columnInt(0));
         case x => x
    }

    def insertWordDescription(word_id : Int, description_id : Int) = db.query("""SELECT word_description_id FROM word_description WHERE word_id=? and description_id=?""",x => x.columnInt(0), word_id, description_id) match {
         case Stream() => db.update("""INSERT into word_description (word_id, description_id) VALUES(?, ?)""",word_id, description_id); db.query("select seq from  sqlite_sequence where name = 'word_description'", x => x.columnInt(0));
         case x => x
    }

    def insertWordTag(word_id : Int, tag_id : Int) = db.query("""SELECT word_tag_id FROM word_tag WHERE word_id=? and tag_id=?""",x => x.columnInt(0), word_id, tag_id) match {
         case Stream() => db.update("""INSERT into word_tag (word_id, tag_id) VALUES(?, ?)""",word_id, tag_id); db.query("select seq from  sqlite_sequence where name = 'word_tag'", x => x.columnInt(0));
         case x => x
    }

    def insertWordTags(word_ids : List[Int], tag_ids : List[Int]) = for (word_id <- word_ids; tag_id <- tag_ids) yield ( insertWordTag(word_id, tag_id) )

    def loadDescriptions(descriptions : Stream[(List[Word], List[Word])], tags : List[Word] = List()) {
        for ( (words, descriptions) <- descriptions ) {
            db.update("""BEGIN TRANSACTION""")
            lazy val tag_ids = insertTags(tags)
            for { 
                word_id <- (for ( word <- words ) yield insertWord(word)).flatten
                description_id <- (for ( description <- descriptions ) yield insertDescription(description)).flatten
            } yield {
                insertWordTags(List(word_id),tag_ids)
                insertWordDescription(word_id, description_id)
            }
            db.update("""COMMIT""")
        }
    }

    def loadPhraseNWords(phraseNWords: Stream[(String, List[String])], tags : List[Word] = List()) {
        var word_phrase_queue_number = db.query("""SELECT min(queue_number) FROM word_phrase_queue""", x => x.columnDouble(0)).headOption getOrElse 1.0
        var word_description_queue_number = db.query("""SELECT min(queue_number) FROM word_description_queue""", x => x.columnDouble(0)).headOption getOrElse 1.0
        var word_picture_queue_number = db.query("""SELECT min(queue_number) FROM word_picture_queue""", x => x.columnDouble(0)).headOption getOrElse 1.0
        var phrase_word_queue_number = db.query("""SELECT min(queue_number) FROM phrase_word_queue""", x => x.columnDouble(0)).headOption getOrElse 1.0

            db.update("""BEGIN TRANSACTION""")
        for ( ((phrase : String, words ),index) <- phraseNWords.zipWithIndex ) {
            //db.update("""INSERT INTO "phrase" (phrase_value) VALUES(?)""",phrase)

            val phrase_ids = db.query("""SELECT phrase_id FROM phrase WHERE phrase_value=?""",x => x.columnInt(0), phrase) match {
                case Stream() => {
                     db.update("""INSERT INTO "phrase" (phrase_value) VALUES(?)""",phrase); 
                     db.query("""SELECT phrase_id FROM phrase WHERE phrase_value=?""",x => x.columnInt(0), phrase) 
                     for (phrase_id <- db.query("""SELECT phrase_id FROM phrase WHERE phrase_value=?""",x => x.columnInt(0), phrase)) yield {
                         phrase_word_queue_number = phrase_word_queue_number - 1
                         db.update("""INSERT INTO phrase_word_queue (phrase_id, queue_number)  VALUES(?,?)""", phrase_id, phrase_word_queue_number)
                         phrase_id
                     }
                }
                case x => x
            }

            for ( (word : String) <- "" :: "" :: words ) {
                val word_ids = db.query("""SELECT word_id FROM word WHERE word_value=?""",x => x.columnInt(0), word) match {
                    case Stream() => {
                         db.update("""INSERT into word (word_value) VALUES(?)""",word);
                         for (word_id <- db.query("""SELECT word_id FROM word WHERE word_value=?""",x => x.columnInt(0), word)) yield {
                             word_phrase_queue_number = word_phrase_queue_number - 1
                             db.update("""INSERT INTO word_phrase_queue (word_id, queue_number) VALUES(?,?)""", word_id, word_phrase_queue_number)
                             word_description_queue_number = word_description_queue_number - 1
                             db.update("""INSERT INTO word_description_queue (word_id, queue_number) VALUES(?,?)""", word_id, word_description_queue_number)
                             word_picture_queue_number = word_picture_queue_number - 1
                             db.update("""INSERT INTO word_picture_queue (word_id, queue_number) VALUES(?,?)""", word_id, word_picture_queue_number)
                             word_id
                         }
                    }
                    case word_ids => {
                         for (word_id <- word_ids) {
                             db.update("""UPDATE word SET word_frequency=word_frequency+1 where word_id=?""",word_id)
                         }
                         word_ids
                    }   
                }

                for (phrase_id <- phrase_ids; word_id <- word_ids) {
                    db.query("""SELECT word_phrase_id FROM word_phrase WHERE word_id=? and phrase_id=?""",x => x.columnInt(0), word_id,phrase_id) match {
                         case Stream() => {
                             db.update("""INSERT INTO word_phrase (word_id, phrase_id) VALUES(?,?)""",word_id,phrase_id)
                         }
                         case xs => {}
                    }
                }
            }

            for ( x :: y :: List() <- words.sliding(2) ) {
                db.update("""REPLACE into "hmm1" (hmm1_word1_id, hmm1_emit_id, hmm1_frequency) SELECT key1.word_id,emit.word_id, ifnull((select hmm1_frequency+1 from hmm1 where hmm1.hmm1_word1_id = key1.word_id and hmm1.hmm1_emit_id = emit.word_id),1) FROM word as key1, word as emit WHERE key1.word_value=? and emit.word_value=?""",x,y)
            }

            for ( x :: y :: z :: List() <- words.sliding(3) ) {
                db.update("""REPLACE into "hmm2" (hmm2_word1_id, hmm2_word2_id, hmm2_emit_id, hmm2_frequency) SELECT key1.word_id, key2.word_id, emit.word_id, ifnull((select hmm2_frequency+1 from hmm2 where hmm2.hmm2_word1_id = key1.word_id and hmm2.hmm2_word2_id = key2.word_id and hmm2.hmm2_emit_id = emit.word_id),1) FROM word as key1, word as key2, word as emit WHERE key1.word_value=? and key2.word_value=? and emit.word_value=?""",x,y,z)
            }

            if (index % 50 == 0) {
                  println("Index: " + index)
                 db.update("""COMMIT""")
                 db.update("""BEGIN TRANSACTION""")
            }
        }

            db.update("""COMMIT""")

        /*        
        db.update("insert into picture_word_queue (picture_id) select picture_id from picture")
        db.update("insert into description_word_queue (description_id) select description_id from description")
        */
    }
}
