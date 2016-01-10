package ru.wordmetrix.dreamcrammer.db
import io.Source

class LoadText(db : DB) extends Load(db) {
   var count = 0
   def apply(source : Source, tags : List[Word]) {
       println("!!")
       loadPhraseNWords( phraseNWords(source.getLines/*map(x => { count = count + 1; if (count % 10000 == 0) println(count); x} ))*/.mkString("")), tags)
   }
}

