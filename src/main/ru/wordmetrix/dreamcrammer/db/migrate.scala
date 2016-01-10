package ru.wordmetrix.dreamcrammer.db
import io.Source

class Migrate(db : DB) extends Load(db) {
    def elicitRecords(source : Source) = source.getLines.map(_.split(":") match { 
        case Array(x,y) => Some((x,y))
        case x => None 
    }).flatten

    def apply(source : Source, tags : List[Word]) {
        loadPhraseNWords(elicitRecords(source).map({
            case (_,y) => println(y); y.split("""\s*;\s*""").map(_.trim).filter(x => rPhrase.findFirstIn(x) != None).map(x => {printf(">%s<\n",x); phraseNWords(x)})
        }).flatten.flatten.toStream, tags)

        loadDescriptions(elicitRecords(source.reset).map({
            case (x,y) => (
                x.trim.split("""\s*;\s*""").map(_.trim).toList, 
                y.trim.split("""\s*;\s*""").map(_.trim).filter(x => rPhrase.findFirstIn(x) == None).toList
            )
        }).toStream, tags)
    }
}

