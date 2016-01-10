package ru.wordmetrix.dreamcrammer.db
import ru.wordmetrix.nlp._

class W0CP(implicit db: DB) extends Map[Word, Double] {
    override def apply(w: Word) = w.frequency.toDouble

    def +[B1 >: Double](kv: (Word, B1)): W0CP = this //for awhile 

    def -(key: Word): W0CP = this

    def get(key: Word): Option[Double] = Some(apply(key))

    def iterator: Iterator[(Word, Double)] = db.query("""
            SELECT id,word_frequency FROM word""",
        x => (new Word(x.columnInt(0)), x.columnDouble(1))).toIterator
}

class WordProbabilities(implicit db: DB) extends MP0Able[Word] {
    def apply(): Map[Word, Double] = new W0CP
}

class W1CP(word: Word)(implicit db: DB) extends W0CP {
    override def apply(w: Word) = get(w).get

    override def +[B1 >: Double](kv: (Word, B1)): W1CP = 
        throw new java.lang.UnsupportedOperationException()

    override def -(key: Word): W1CP = 
        throw new java.lang.UnsupportedOperationException()

    override def get(key: Word): Option[Double] = getCount(key) match {
        case Some(n) => Some(n / word.frequency)
        case None    => Some(0)
    }

    def getCount(key: Word) = db.query("""
        SELECT hmm1_frequency 
        FROM hmm1
        WHERE hmm1_word1_id = ? and hmm1_emit_id = ?""",
        x => x.columnDouble(0),
        word.id,
        key.id).headOption

    override def iterator: Iterator[(Word, Double)] = db.query("""
        SELECT hmm1_emit_id, hmm1_frequency 
        FROM hmm1
        WHERE hmm1_word1_id = ?""",
        x => (new Word(x.columnInt(0)), x.columnDouble(1)),
        word.id).toIterator
}

class W1CProbabilities(implicit db: DB) extends MPAble[Word, Word] {
    override def apply(w: Word) = new W1CP(w)

    def +[B1 >: Map[Word, Double]](kv: (Word, B1)): W1CProbabilities = this
    //for awhile 

    def -(key: Word): W1CProbabilities = this

    def get(key: Word): Option[W1CP] = Some(apply(key))

    def iterator: Iterator[(Word, W1CP)] = db.query("""
        SELECT hmm1_word1_id FROM hmm1""",
        x => new Word(x.columnInt(0))).toIterator.map(x => (x, new W1CP(x)))
}

class W2CP(word1: Word, word2: Word)(implicit db: DB) extends W1CP(word1) {

    override def get(key: Word): Option[Double] = getCount(key) match {
        case Some(n) => Some(n / super.getCount(word2).get)
        case None    => Some(0)
    }
    override def getCount(key: Word): Option[Double] = db.query("""
        SELECT hmm2_frequency 
        FROM hmm2
        WHERE hmm2_word1_id = ? and hmm2_word2_id = ? and hmm2_emit_id = ?""",
        x => x.columnDouble(0),
        word1.id,
        word2.id,
        key.id).headOption

    override def iterator: Iterator[(Word, Double)] = db.query("""
        SELECT hmm2_emit_id, hmm2_frequency/hmm1_frequency 
        FROM hmm2,hmm1
        WHERE
            hmm1_word1_id = hmm2_word1_id and
            hmm1_emit_id = hmm2_word2_id and
            hmm2_word1_id = ? and hmm2_word2_id = ?""",
        x => (new Word(x.columnInt(0)), x.columnDouble(1)),
        word1.id,
        word2.id).toIterator
}

class W2CProbabilities(implicit db: DB) extends MPAble[(Word, Word), Word] {
    override def apply(key: (Word, Word)) = new W2CP(key._1, key._2)

    def +[B1 >: Map[Word, Double]](kv: ((Word, Word), B1)): W2CProbabilities =
        this //for awhile 

    def -(key: (Word, Word)): W2CProbabilities = this

    def get(key: (Word, Word)): Option[W2CP] = Some(apply(key))

    def iterator: Iterator[((Word, Word), W2CP)] = db.query("""
        SELECT hmm1_word1_id FROM hmm1""",
        x => (new Word(x.columnInt(0)), new Word(x.columnInt(1)))).
        toIterator.map(x => (x, new W2CP(x._1, x._2)))
}

object Analyze {
    implicit def string2Analyze(s: String)(implicit db: DB): Analyze =
        new Analyze(s)

    implicit def string2HMM(s: String)(
        implicit hmmdb: HMMDBAble[Word], db: DB) =
        new HMM(List("", "").map(Word.query).flatten ++ s.tokenize())
}

class Analyze(s: String)(implicit db: DB) {
//    import ru.wordmetrix.nlp.NLP._
    def tokenize() = new ru.wordmetrix.nlp.NLP(s).tokenize.
        map(w => {println("query " + w); Word.query(w)}).flatten
}
