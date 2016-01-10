package ru.wordmetrix.nlp
import NLP._

abstract trait MP0Able[W] {
    def apply(): Map[W, Double]
}

abstract trait MPAble[T, W] extends MP0Able[W] with Map[T, Map[W, Double]] {
    def apply(t: T): Map[W, Double]
    def apply() = Map()
}

class MPS[W](val hmm0: MP0Able[W], val hmm1: MPAble[(W), W], val hmm2: MPAble[(W, W), W]) {
    def estimate(list: List[W]): Double = {
        println("estimate!!", list);
        list match {
            case w1 :: w2 :: w3 :: List() => hmm2.getOrElse((w1, w2), Map()).getOrElse(w3, estimate(List(w2, w3)))
            case w1 :: w2 :: List()       => hmm1.getOrElse(w1, Map()).getOrElse(w2, estimate(List(w2)))
            case w1 :: List()             => hmm0().getOrElse(w1, estimate(List(w1)))
            case List()                   => 1d
        }
    }

    def suggest(list: List[W]): Map[W, Double] = {
        println(list)
        list match {
        case w1 :: w2 :: ws => hmm2.getOrElse((w1, w2), suggest(w2 :: ws))
        case w1 :: ws       => hmm1.getOrElse((w1), suggest(ws))
        case List()         => hmm0()
    }}
}

class MP0(map: Map[NLP.Word, Double]) extends MP0Able[NLP.Word] {
    def apply() = map
}

class MPN[T](val map: Map[T, Map[NLP.Word, Double]], divider: T => Double) extends MPAble[T, NLP.Word] {
    override def apply(key: T) = get(key).get

    def +[B >: Map[NLP.Word, Double]](kv: (T, B)) =
        new MPN[T](map + (kv._1 -> kv._2.asInstanceOf[Map[NLP.Word, Double]]), 
                divider)

    def -(t: T) = new MPN[T](map - t, divider)

    def get(key: T): Option[Map[ru.wordmetrix.nlp.NLP.Word, Double]] =
        map.get(key) match {
            case Some(map) => Some(map.map({
                case (x, y) => println(key); println("ww", x, y, key, divider(key)); (x -> y / divider(key))
            }))
            case None => None
        }
    def iterator: Iterator[(T, Map[ru.wordmetrix.nlp.NLP.Word, Double])] = 
        map.keysIterator.map(x => x->apply(x))
}

abstract trait HMMDBAble[W] {
    val frw: MPS[W]
    val rev: MPS[W]
}

class HMMDB[W](val frw: MPS[W], val rev: MPS[W]) extends HMMDBAble[W] {

}

object Dist {
    implicit def seq2Dist[T](seq: Seq[Seq[T]]) = new Dist[T](seq)
}

class Dist[T](seq: Seq[Seq[T]]) {
    def dist(implicit split: Seq[T] => (Seq[T], Seq[T])) =
        seq.map(split).groupBy(_._1).map({
            case (x, y) => (x, y.map(_._2))
        })
}

object Count {
    implicit def apply[W](seq: Seq[W]) = {
        new Count(seq)
    }

    //  implicit def seq2Count[W](seq : Seq[W]) = new Count(seq) 
}

class Count[W](seq: Seq[W]) {
    def count(): Map[W, Double] = seq.groupBy(x => x).map({
        case (x, y) => (x, y.length.toDouble)
    }).toMap
    def count1() = count()
}

object HMM {
    type Phrase = String
    type Word = String

    implicit def string2HMM(s: String)(implicit hmm: HMMDBAble[NLP.Word]) =
        new HMM(List("", "") ++ string2NLP(s).tokenize)

    import Dist._
    import Count._

    def analyze(phrase: List[NLP.Word]): HMMDB[NLP.Word] =
        analyzeslides((List("", "") ++ phrase).sliding(3).toList)

    def analyzetext(s: String) = {
        val phrases = s.phrases.map(x => x.tokenizeGap()).toList

        val mp0 = new MP0(
            phrases.map(_.sliding(1)).flatten.flatten.count())

        val mp1 = new MPN(
            phrases.map(_.sliding(2)).flatten.map({
                case x :: y :: xs => (x, y)
            }).toList.groupBy(_._1).map({
                case (t, ws) => (t, ws.map(_._2).count())
            }).toMap,
            (x: NLP.Word) => mp0()(x))

        val mp2 = new MPN[(NLP.Word, NLP.Word)](
            phrases.map(_.sliding(3)).flatten.map({
                case x :: y :: z :: xs => ((x, y), z)
            }).toList.groupBy(_._1).map({
                case (t, ws) => (t, ws.map(_._2).count())
            }).toMap,
            (x: (NLP.Word, NLP.Word)) => mp1.map(x._1)(x._2))

        val mp1rev = mp1

        val mp2rev = mp2

        new HMMDB[NLP.Word](new MPS(mp0, mp1, mp2),
            new MPS(mp0, mp1rev, mp2rev))
    }

    /*analyzeslides(
        s.phrases.map(x => x.tokenizeGap().sliding(3)).flatten.toList)
*/
    def analyzeslides(slides: List[List[NLP.Word]]): HMMDB[NLP.Word] = {
        val mp0 = new MP0((slides.map(_.drop(2).head)).count())
        println(mp0())

        val mp1 = new MPN(slides.map({
            case x :: y :: z :: List() => (y, z)
        }).toList.groupBy(_._1).map({
            case (t, ws) => (t, ws.map(_._2).count())
        }).toMap, (x: NLP.Word) => mp0()(x))

        val mp2 = new MPN(slides.map({
            case x :: y :: z :: List() => ((x, y), z)
        }).toList.groupBy(_._1).map({
            case (t, ws) => (t, ws.map(_._2).count())
        }).toMap, (x: (NLP.Word, NLP.Word)) => mp1(x._1)(x._2))

        val mp1rev = new MPN(slides.map({
            case x :: y :: z :: List() => (z, y)
        }).toList.groupBy(_._1).map({
            case (t, ws) => (t, ws.map(_._2).count())
        }).toMap, (x: NLP.Word) => mp0()(x))

        val mp2rev = new MPN(slides.map({
            case x :: y :: z :: List() => ((y, z), x)
        }).toList.groupBy(_._1).map({
            case (t, ws) => (t, ws.map(_._2).count())
        }).toMap, (x: (NLP.Word, NLP.Word)) => mp1rev(x._1)(x._2))

        new HMMDB[NLP.Word](new MPS(mp0, mp1, mp2),
            new MPS(mp0, mp1rev, mp2rev))
    }
}

class HMM[W](val phrase: List[W])(implicit hmmdb: HMMDBAble[W]) {

    val frw = hmmdb.frw
    val rev = hmmdb.rev

    def estimate(): Double = weights().foldLeft(1d)({
        case (p, weight) => p * weight
    })

    implicit def iterator2List[T](it: Iterator[T]): List[T] = it.toList

    def weights(): List[Double] =
        gapped.sliding(3).map(frw.estimate)

    def suggest(n: Int, p: List[W] = gapped): Map[W, Double] =
        n match {
            case n if n > 2 => suggest(n - 1, p.tail)
            case 2          => frw.suggest(p)
            case 1 | 0      => frw.suggest(p) ++ rev.suggest(p.tail.take(2)) // ticket: it's debatable 
        }

    def suggests(p: List[W] = gapped): Stream[Map[W, Double]] =
        p.take(2) match {
            case hp if hp.length > 0 => frw.suggest(hp) #:: suggests(p.tail)
            case _                   => Stream()
        }

    class Lazy(ws: => List[W]) {
        lazy val suggest = frw.suggest(ws)
    }

    object Lazy {
        def apply(ws: List[W]) = new Lazy(ws)
        implicit def lazy2Map(lz: Lazy) = {
            println("make lazy for", lz)
            lz.suggest
        }
    }

    def gapped = /*List("", "") ++*/ phrase

    def suggesttest(n: Int): Map[W, Double] = suggests().drop(n - 2).head
}
