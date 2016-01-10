package ru.wordmetrix.dreamcrammer.db
import io.Source

class LoadList(db : DB) extends Load(db) {
   def apply(source : Source, tags : List[Word]) {
       db.update("BEGIN TRANSACTION")
       for ( word_id <- insertWords(source.getLines.map(_.trim.toLowerCase).toStream); word_tag_id <- insertTags(tags) ) {
           insertWordTag(word_id, word_tag_id)
       }
       db.update("COMMIT")
   }
}

