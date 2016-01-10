package ru.wordmetrix.dreamcrammer.db

import scala.collection.mutable.PriorityQueue
import scala.util.Random
import android.os.AsyncTask
import ru.wordmetrix._
import java.security.MessageDigest

abstract class Field(val id: Int) {
    def print = printf(" -- id = %s, field\n", id)
    def refresh(): Field
    // ticket : to implement delete function for all types of field.
    def delete(): Unit

}

object Word {
    def query(value: String)(implicit db: DB) = {
        db.query("""select word_id from word where word_value=?""", x => new Word(x.columnInt(0)), value).headOption
    }

    def queryOrCreate(value: String)(implicit db: DB) = query(value) getOrElse new Word(value)
}

class Word(id: Int)(implicit val db: DB) extends Field(id) {
    lazy val convertors = new Convertors

    def this(value: String)(implicit db: DB) = this({
        db.update("""insert into word (word_value, word_date) values(?, strftime('%s','now'))""", value)
        db.query("""select seq from sqlite_sequence where name = 'word'""", _.columnInt(0)).head
    })

    def register() = {
        convertors.word2phrases.register(this)
        convertors.word2pictures.register(this)
        convertors.word2descriptions.register(this)

        this
    }

    def disable(phrase: Phrase, disable: Boolean): Boolean = {
        db.update(
            """ update word_phrase 
                set word_phrase_disabled = ? 
                where word_id = ? and phrase_id = ?""",
            disable, id, phrase.id)
        disable
    }

    def disable(phrase: Phrase): Boolean = db.query(
        """ select word_phrase_disabled
            from word_phrase
            where word_id = ? and phrase_id = ?""",
        x => x.columnBoolean(0),
        id, phrase.id).headOption getOrElse true

    def disable(picture: Picture, disable: Boolean): Boolean = {
        db.update(
            """ update word_picture 
                set word_picture_disabled = ? 
                where word_id = ? and picture_id = ?""",
            disable, id, picture.id)
        disable
    }

    def disable(picture: Picture): Boolean = db.query(
        """ select word_picture_disabled
            from word_picture
            where word_id = ? and picture_id = ?""",
        x => x.columnBoolean(0),
        id, picture.id).headOption getOrElse true

    def delete() = db.update("delete from word where word_id=?", id)
    def refresh() = new Word(id)

    lazy val value = db.query("select word_value from word where word_id=?", x => x.columnString(0), id).mkString(" ")
    lazy val phrases = new Convertors().word2phrases.feed(this)
    lazy val samples = new Convertors().word2phrases.feed(this).take(5)
    lazy val descriptions = new Convertors().word2descriptions.feed(this)
    lazy val pictures = new Convertors().word2pictures.feed(this)

    lazy val otherphrases = db.query("select phrase.phrase_id, phrase_value from phrase, word_phrase where word_id=? and word_phrase.phrase_id = phrase.phrase_id and not phrase_is_pivotal",
        x => new Phrase(x.columnInt(0)) {
            val _value = x.columnString(1)
            override lazy val value = _value
        },
        id).toList

    lazy val (age, frequency, status) = db.query("""
            SELECT round(julianday(datetime())-julianday(datetime(word_date, 'unixepoch'))),
            	word_frequency,
            	word_status 
            from word 
            where word_id=?""",
        x => (x.columnInt(0), x.columnInt(1), x.columnInt(2)),
        id).headOption getOrElse (0, 0, 0)

    def join(phrase: Phrase) = {
        db.query(
            "select word_phrase_id from word_phrase where word_id=? and phrase_id=?",
            x => x.columnInt(0),
            id, phrase.id).headOption getOrElse db.update("insert into word_phrase (word_id, phrase_id) values(?,?)", id, phrase.id)
    }

    def join(picture: Picture) = db.update("insert into word_picture (word_id, picture_id) values(?,?)", id, picture.id)

    def join(description: Description) = db.update("insert into word_description (word_id, description_id) values(?,?)", id, description.id)

    def removePicture(picture: Picture) = new Convertors().word2pictures.disable(this, picture)

    def setStatus(track: Int) = db.update("update word set word_status=? where word_id=?", track, id)

    def setValue(track: String) = db.update("update word set word_value=? where word_id=?", track, id)

    var _is_seen: Option[Boolean] = None
    def is_seen = _is_seen match {
        case None => {
            _is_seen = Some(db.query("select word_is_seen from word where word_id=?", x => x.columnInt(0) != 0, id).reduce(_ & _))
            _is_seen.get
        }
        case Some(x) => x
    }

    def is_seen(x: Boolean) = {
        db.update("update word set word_is_seen=? where word_id=?", x, id);
        _is_seen = Some(x)
        x
    }

    //lazy val frequency = db.query("select word_frequency from word where word_id=?",x => x.columnInt(0),id).reduce(_+_)

    def _track(x: DB.Access): OptionData[Array[Byte]] = _track(x, 0, 1)
    def _track(x: DB.Access, i: Int, j: Int): OptionData[Array[Byte]] = {
        log("Track: %s", x.columnInt(j))
        x.columnInt(j) match {
            case 0 => SomeData(x.columnBlob(i))
            case 1 => NoData
            case x => NeverData
        }
    }

    lazy val track = db.query("select word_track, word_track_status from word where word_id=?", _track, id).head
    def setTrack(track: OptionData[Array[Byte]]) = track match {
        case SomeData(track) => db.update("update word set word_track=?, word_track_status=0 where word_id=?", track, id)
        case track           => db.update("update word set word_track=null, word_track_status=? where word_id=?", track match { case NoData => 1; case NeverData => 2 }, id)
    }

    def _ipa(x: DB.Access): OptionData[String] = _ipa(x, 0, 1)
    def _ipa(x: DB.Access, i: Int, j: Int): OptionData[String] = {
        log("IPA: %s", x.columnInt(j))
        x.columnInt(j) match {
            case 0 => SomeData(x.columnString(i))
            case 1 => NoData
            case x => NeverData
        }
    }

    lazy val ipa = db.query("select word_ipa, word_ipa_status from word where word_id=?", _ipa, id).head
    def setIPA(track: OptionData[String]) = track match {
        case SomeData(ipa) => db.update("update word set word_ipa=?, word_ipa_status=0 where word_id=?", ipa, id)
        case ipa           => db.update("update word set word_ipa=null, word_ipa_status=? where word_id=?", ipa match { case NoData => 1; case NeverData => 2 }, id)
    }

    def lastseen() = {
        val lastseen = db.query("select word_lastseen from word where word_id=?", x => x.columnInt(0), id).head
        db.update("update word set word_lastseen=strftime('%s','now') where word_id=?", id)
        lastseen
    }

    def addPicture(image: Array[Byte]): Picture = {
        addPicture(db.query("select picture_id from picture where picture_md5 = ? limit 1", x => new Picture(x.columnInt(0)), MD5(image)).headOption getOrElse {
            log("New picture is gonna be created")
            db.update("insert into picture (picture_value, picture_body) values(?,?)", value, image)
            db.query("select seq from sqlite_sequence where name='picture'", x => new Picture(x.columnInt(0))).head
        })
    }

    def addPicture(picture: Picture): Picture = {
        db.query("select picture_id from word_picture where word_id = ? and picture_id = ? limit 1", _.columnInt(0), id, picture.id).headOption match {
            case None => {
                log("New picture is gonna be added")
                db.update("insert into word_picture (word_id, picture_id) VALUES(?, ?)", id, picture.id)
                new Convertors().picture2words.register(picture)
            }
            case Some(x) => {
                log("This picture already exists")
                picture
            }
        }
    }

    def addPicture(value: String): Picture = {
        db.update("insert into picture (picture_value) values(?)", value)
        addPicture(db.query("select seq from sqlite_sequence where name='picture'", x => new Picture(x.columnInt(0))).head)
    }

    def addDescription(description: Description): Description = {
        db.update("insert into word_description (word_id, description_id) VALUES(?,?)", id, description.id)
        convertors.description2words.register(description)
    }

    def addDescription(value: String): Description = {
        db.update("insert into description (description_value) values(?)", value)
        addDescription(db.query("select seq from sqlite_sequence where name='description'", x => new Description(x.columnInt(0))).head)
    }

    def addPhrase(phrase: Phrase): Phrase = {
        db.update("insert into word_phrase (word_id, phrase_id) VALUES(?,?)", id, phrase.id)
        convertors.phrase2words.register(phrase)

    }

    def addPhrase(value: String): Phrase = {
        db.update("insert into phrase (phrase_value, phrase_is_pivotal) values(?,1)", value)
        addPhrase(db.query("select seq from sqlite_sequence where name='phrase'", x => new Phrase(x.columnInt(0))).head)
    }

    override def print = {
        printf(" -- word = %s\n", value)
        for (phrase <- phrases.take(5)) phrase.print
        for (description <- descriptions) description.print
        for (picture <- pictures) picture.print
        printf(" ----------\n")
    }

    override def toString = "Word(%s)".format(value)
}

class Phrase(id: Int)(implicit val db: DB) extends Field(id) {
    lazy val value = db.query("select phrase_value from phrase where phrase_id=? ", x => x.columnString(0), id).mkString(" ")

    def is_pivotal = db.query("select phrase_is_pivotal from phrase where phrase_id=?", x => x.columnBoolean(0), id).reduce(_ & _)

    def delete() = db.update("delete from phrase where phrase_id=?", id)

    def is_pivotal(is_pivotal: Boolean) = {
        db.update("update phrase set phrase_is_pivotal=? where phrase_id = ?", is_pivotal, id)
    }

    def setValue(track: String) = db.update("update phrase set phrase_value=? where phrase_id=?", track, id)
    def refresh() = new Phrase(id)

    override def print = printf(" -- phrase = %s, word\n", value)

    override def toString = "Phrase(%s)".format(value)
}

class Description(id: Int)(implicit val db: DB) extends Field(id) {
    val value = db.query("select description_value from description where description_id=? ", x => x.columnString(0), id).mkString(" ")

    override def print = printf(" -- description = %s\n", value)

    def delete() = db.update("delete from description where escription_id=?", id)

    override def toString = "Description(%s)".format(value)

    override def refresh() = { log("This feature has not implemented yet"); this }

}

object MD5 {
    def apply(body: Array[Byte]) = {
        val md = MessageDigest.getInstance("MD5")
        md.update(if (body == null) Array[Byte]() else body)
        md.digest
    }.toList.map("%2h".format(_).takeRight(2)).mkString("").replace(" ", "0")
}

class Picture(id: Int)(implicit val db: DB) extends Field(id) {
    def this(value: String)(implicit db: DB) = this({
        db.update("insert into picture (picture_value) values(?)", value)
        db.update("insert into picture_word_queue (picture_id) select seq from sqlite_sequence where name='picture'")
        db.query("select seq from sqlite_sequence where name='picture'", x => x.columnInt(0)).head
    })

    lazy val value = db.query("select picture_value from picture where picture_id=? ", x => x.columnString(0), id).mkString(" ")

    def delete() = db.update("delete from picture where picture_id=?", id)

    //ticket : body should become lazy and united with bodyOption
    //val body = db.query("select picture_body from picture where picture_id=? ",x => x.columnBlob(0),id).head

    def body = bodyOption.get

    lazy val md5 = bodyOption.map(body => {
        db.query("""select picture_md5 from picture where picture_md5 != "" and picture_id=?""", _.columnString(0), id).headOption match {
            case Some(x) => x
            case None => {
                val md5 = MD5(body)
                db.update("UPDATE picture SET picture_md5=? WHERE picture_id=?", md5, id)
                md5
            }
        }
    }) getOrElse ""

    val bodyOption = db.query(
        "select picture_body, (picture_body is null or (length(picture_body) < 10)) from picture where picture_id=? ",
        x => {
            log("Blob: %s %s", x.columnInt(1), x.columnBlob(0));
            if (x.columnBoolean(1)) None else Some(x.columnBlob(0))
        },
        id).head

    def setValue(track: String) = db.update("update picture set picture_value=? where picture_id=?", track, id)
    def refresh() = new Picture(id)

    def setBody(body: Array[Byte]) = db.update("update picture set picture_body=? where picture_id=?", body, id)

    /*{
        val p = db.asInstanceOf[SQLiteAndroid].db.compileStatement("update picture set picture_body=? where picture_id=?")
        p.bindBlob(1,body)
        p.bindLong(2,id)
        p.execute()
     }*/
    //log("setBody %s",track.size); db.update("update picture set picture_body=? where picture_id=?","x'" + track.map("%2h".format(_).replace(" ","0").takeRight(2)).mkString + "'", id) }

    override def print = printf(" -- picture = %s\n", value)

    override def toString = "Picture(%s)".format(value)
}

abstract trait Join[S <: Field, T <: Field] {
    def feed(s: S): List[T]
    def disable(s: S, t: T, disable: Boolean = true)
}

abstract trait Order[S <: Field, T <: Field] {
    def statistic(): Stream[(Int, Int, Int, String, Double, Int)]
    def statistic_grade(x: Double)
    def statistic_begin()
    def update(r: Record[S, T])
    def disable(r: Record[S, T], disable: Boolean)
    def disable(r: Record[S, T], t: T, disable: Boolean)
    def query: Stream[Record[S, T]]
    def answers(s: S): List[T]
    def incq(): Unit
    //def ahead[E <: Field](e : E) : Unit
    //def ahead(s : S) : Unit
    def register(s: S): S
    def queue_number(): Double
    def queue_number(number: Double): Double
}

class Convertors(implicit db: DB) {
    implicit object word2phrases extends Join[Word, Phrase] with Order[Word, Phrase] {
        def feed(s: Word) = db.query("select phrase.phrase_id, phrase_value from phrase, word_phrase where word_id=? and word_phrase.phrase_id = phrase.phrase_id and phrase_is_pivotal",
            x => new Phrase(x.columnInt(0)) {
                val _value = x.columnString(1)
                override lazy val value = _value
            },
            s.id).toList

        def queue_number() = db.query("SELECT queue_number FROM exercise WHERE exercise_name='word_phrase_queue'", _.columnDouble(0)).head
        def queue_number(number: Double) = { db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='word_phrase_queue'", number); number }

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='word_phrase_queue'""")

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE word_phrase_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_phrase_queue') 
                WHERE word_id=?""", word.id)
        }

        def register(word: Word) = {
            incq()
            db.update("""
               INSERT INTO word_phrase_queue (word_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='word_phrase_queue'))
               """, word.id)
            word
        }

        def ahead(phrase: Phrase) = {
            incq()
            db.update("""
                UPDATE word_phrase_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_phrase_queue') 
                WHERE word_id in (SELECT word_id FROM word_phrase WHERE phrase_id=?) """, phrase.id)
        }

        def statistic() = db.query(
            """
               select (select count(*) from word_phrase_queue join word using(word_id)join word_phrase using(word_id) join phrase using(phrase_id) where phrase_is_pivotal and not word_is_seen), 
                      (select count(*) from word_phrase_queue join word using(word_id)join word_phrase using(word_id) join phrase using(phrase_id) where phrase_is_pivotal and not word_is_seen and date is not null), 
                      (select count(*) from word_phrase_queue join word using(word_id)join word_phrase using(word_id) join phrase using(phrase_id) where phrase_is_pivotal and not word_is_seen and date > exercise_begin), 
                      exercise_name, exercise_grade, strftime('%s','now')-exercise_begin
               from exercise 
               where exercise_id = 1""",
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=1", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=1")

        def update(r: Record[Word, Phrase]) = db.update("update word_phrase_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where word_phrase_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Word, Phrase], disable: Boolean) = db.update("update word_phrase set word_phrase_disabled = ? where word_id in (select word_id from word_phrase_queue where word_phrase_queue_id=?)", disable, r.id)

        def disable(r: Record[Word, Phrase], t: Phrase, disable: Boolean) = db.update("update word_phrase set word_phrase_disabled = ? where word_id in (select word_id from word_phrase_queue where word_phrase_queue_id=?) and phrase_id=?", disable, r.id, t.id)

        def disable(s: Word, t: Phrase, disable: Boolean) = db.update("update word_phrase set word_phrase_disabled = ? where word_id = ? and phrase_id = ?", disable, s.id, t.id)

        def query: Stream[Record[Word, Phrase]] = db.query("""
            select distinct word_phrase_queue_id, word.word_id, word_value, queue_weight, queue_number, date
            from phrase  
            join word_phrase using (phrase_id)
            join word_phrase_queue using (word_id) 
            join word using (word_id) 
            where phrase_is_pivotal and not word_is_seen and (word_status in (2,3)) order by queue_number
            """,
            x => new Record[Word, Phrase](
                x.columnInt(0),
                new Word(x.columnInt(1)) {
                    printf("=> %s %s %s\n", x.columnString(2), x.columnDouble(3), x.columnDouble(4))
                    val _value = x.columnString(2)
                    override lazy val value = _value
                },
                x.columnDouble(3),
                x.columnDouble(4),
                x.columnInt(5)))

        def answers(s: Word): List[Phrase] = s.phrases

    }

    implicit object phrase2words extends Join[Phrase, Word] with Order[Phrase, Word] {
        def feed(s: Phrase) = db.query(
            """ select 
                    word.word_id, word_value 
                from word, word_phrase 
                where phrase_id=? 
                    and word_phrase.word_id = word.word_id 
                    and not word_phrase_disabled
                    and not word_is_seen""",
            x => new Word(x.columnInt(0)) {
                val _value = x.columnString(1)
                override lazy val value = _value
            },
            s.id).toList

        def queue_number() = db.query("""
            SELECT 
                queue_number 
            FROM exercise 
            WHERE exercise_name='phrase_word_queue'""",
            _.columnDouble(0)).head

        def queue_number(number: Double) = {
            db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='phrase_word_queue'", number);
            number
        }

        def register(phrase: Phrase) = {
            incq()
            db.update("""
               INSERT INTO phrase_word_queue (phrase_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='phrase_word_queue'))
               """, phrase.id)
            phrase
        }

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE phrase_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='phrase_word_queue') 
                WHERE phrase_id in (SELECT phrase_id FROM word_phrase WHERE word_id=?) """, word.id)
        }

        def ahead(phrase: Phrase) = {
            incq()
            db.update("""
                UPDATE phrase_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='phrase_word_queue') 
                WHERE phrase_id=?""", phrase)
        }

        def statistic() = db.query(
            """
		select 
                    (select count(*) from phrase_word_queue join phrase using(phrase_id) join word_phrase using(phrase_id) join word using(word_id) where phrase_is_pivotal and not word_is_seen), 
                    (select count(*) from phrase_word_queue join phrase using(phrase_id) join word_phrase using(phrase_id) join word using(word_id) where phrase_is_pivotal and not word_is_seen and date is not null), 
                    (select count(*) from phrase_word_queue join phrase using(phrase_id) join word_phrase using(phrase_id) join word using(word_id) where phrase_is_pivotal and not word_is_seen and date > exercise_begin), 
                    exercise_name, exercise_grade, strftime('%s','now') - exercise_begin 
                from exercise 
                where exercise_id = 4 """,
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='phrase_word_queue'""")

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=4", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=4")

        def update(r: Record[Phrase, Word]) = db.update("update phrase_word_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where phrase_word_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Phrase, Word], disable: Boolean) = db.update(
            """update word_phrase 
                set word_phrase_disabled = ? 
                where 
                    phrase_id in (
                        select phrase_id 
                        from phrase_word_queue 
                        where phrase_word_queue_id=?
                    )
            """, disable, r.id)

        def disable(r: Record[Phrase, Word], t: Word, disable: Boolean) = db.update(
            """ update word_phrase 
                set word_phrase_disabled = ? 
                where phrase_id in (
                    select phrase_id 
                    from phrase_word_queue 
                    where phrase_word_queue_id=?) and word_id=?
            """, disable, r.id, t.id)

        def disable(s: Phrase, t: Word, disable: Boolean) = db.update(
            """ update word_phrase 
                set word_phrase_disabled = ? 
                where phrase_id = ? and word_id = ?
            """, disable, s.id, t.id)

        def query: Stream[Record[Phrase, Word]] = db.query(
            """
                select distinct 
                    phrase_word_queue_id, phrase.phrase_id, phrase_value, queue_weight, queue_number, date
                    from phrase_word_queue 
                        join phrase using (phrase_id) 
                        join word_phrase using (phrase_id) 
                        join word using (word_id) 
                    where 
                        not word_is_seen 
                        and phrase_is_pivotal 
                        and (word_status in (2,3)) 
                        and not word_phrase_disabled 
                    order by queue_number """,

            x => new Record[Phrase, Word](
                x.columnInt(0),
                new Phrase(x.columnInt(1)) {
                    val _value = x.columnString(2)
                    override lazy val value = _value
                },
                x.columnDouble(3),
                x.columnDouble(4),
                x.columnInt(5)))

        def answers(s: Phrase): List[Word] = phrase2words.feed(s)
    }

    implicit object word2pictures extends Join[Word, Picture] with Order[Word, Picture] {
        def feed(s: Word) = db.query(
            """ select 
                    picture.picture_id, picture_value 
                from 
                    picture, word_picture 
                where word_id=? 
                    and word_picture.picture_id = picture.picture_id 
                    and not word_picture_disabled""",
            x => new Picture(x.columnInt(0)) { val _value = x.columnString(1); override lazy val value = _value },
            s.id).toList

        def queue_number() = db.query("SELECT queue_number FROM exercise WHERE exercise_name='word_picture_queue'", _.columnDouble(0)).head
        def queue_number(number: Double) = { db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='word_picture_queue'", number); number }

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='word_picture_queue'""")

        def register(word: Word) = {
            incq()
            db.update("""
               INSERT INTO word_picture_queue (word_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='word_picture_queue'))
               """, word.id)
            word
        }

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE word_picture_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_picture_queue') 
                WHERE word_id=?""", word.id)
        }

        def ahead(picture: Picture) = {
            incq()
            db.update("""
                UPDATE word_picture_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_picture_queue') 
                WHERE word_id in (SELECT word_id FROM word_picture WHERE picture_id=?) """, picture.id)
        }

        def statistic() = db.query(
            "select (select count(*) from word_picture_queue), (select count(*) from word_picture_queue where date is not null), (select count(*) from word_picture_queue where date > exercise_begin), exercise_name, exercise_grade, strftime('%s','now')-exercise_begin from exercise where exercise_id = 2",
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=2", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=2")

        def update(r: Record[Word, Picture]) = db.update("update word_picture_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where word_picture_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Word, Picture], disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where word_id in (select word_id from word_picture_queue where word_picture_queue_id=?)", disable, r.id)

        def disable(r: Record[Word, Picture], t: Picture, disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where word_id in (select word_id from word_picture_queue where word_picture_queue_id=?) and picture_id=?", disable, r.id, t.id)

        def disable(s: Word, t: Picture, disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where word_id = ? and pictures_id = ?", disable, s.id, t.id)

        def query: Stream[Record[Word, Picture]] = db.query("""
            select distinct 
                word_picture_queue_id, word.word_id, word_value, queue_weight, queue_number, date 
            from 
                word_picture_queue join word using(word_id) join word_picture using (word_id)  
            where 
                not word_is_seen and not word_picture_disabled order by queue_number""",

            x => new Record[Word, Picture](
                x.columnInt(0),
                new Word(x.columnInt(1)) {
                    printf("=> %s %s %s\n", x.columnString(2), x.columnDouble(3), x.columnDouble(4),
                        x.columnInt(5))
                    val _value = x.columnString(2)
                    override lazy val value = _value
                },
                x.columnDouble(3),
                x.columnDouble(4)))

        def answers(s: Word): List[Picture] = s.pictures
    }

    implicit object picture2words extends Join[Picture, Word] with Order[Picture, Word] {
        def feed(s: Picture) = db.query("select word.word_id, word_value from word, word_picture where picture_id=? and word_picture.word_id = word.word_id",
            x => new Word(x.columnInt(0)) {
                val _value = x.columnString(1)
                override lazy val value = _value
            },
            s.id).toList

        def queue_number() = db.query("SELECT queue_number FROM exercise WHERE exercise_name='picture_word_queue'", _.columnDouble(0)).head
        def queue_number(number: Double) = { db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='picture_word_queue'", number); number }

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='picture_word_queue'""")

        def register(picture: Picture) = {
            incq()
            db.update("""
               INSERT INTO picture_word_queue (picture_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='picture_word_queue'))
               """, picture.id)
            picture
        }

        def ahead(picture: Picture) = {
            incq()
            db.update("""
                UPDATE picture_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='picture_word_queue') 
                WHERE picture_id=?""", picture.id)
        }

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE picture_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='picture_word_queue') 
                WHERE picture_id in (SELECT picture_id FROM word_picture WHERE word_id=?) """, word.id)
        }

        def statistic() = db.query(
            "select (select count(*) from picture_word_queue), (select count(*) from picture_word_queue where date is not null), (select count(*) from picture_word_queue where date > exercise_begin), exercise_name, exercise_grade, strftime('%s','now')-exercise_begin from exercise where exercise_id = 5",
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=5", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=5")

        def update(r: Record[Picture, Word]) = db.update("update picture_word_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where picture_word_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Picture, Word], disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where picture_id in (select picture_id from picture_word_queue where picture_word_queue_id=?)", disable, r.id)

        def disable(r: Record[Picture, Word], t: Word, disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where picture_id in (select picture_id from picture_word_queue where picture_word_queue_id=?) and word_id=?", disable, r.id, t.id)

        def disable(s: Picture, t: Word, disable: Boolean) = db.update("update word_picture set word_picture_disabled = ? where picture_id = ? and word_id = ?", disable, s.id, t.id)

        def query: Stream[Record[Picture, Word]] = db.query("select distinct picture_word_queue_id, picture.picture_id, picture_value, queue_weight, queue_number, date from picture_word_queue join picture using (picture_id) join word_picture using (picture_id) join word using(word_id) where not word_is_seen and not word_picture_disabled order by queue_number",
            x => {
                println(x.columnInt(0), x.columnInt(1), x.columnString(2), x.columnDouble(3), x.columnDouble(4),
                    x.columnInt(5))
                new Record[Picture, Word](
                    x.columnInt(0),
                    new Picture(x.columnInt(1)) {
                        val _value = x.columnString(2)
                        override lazy val value = _value

                    },
                    x.columnDouble(3),
                    x.columnDouble(4),
                    x.columnInt(5) // select phrase.phrase_id from phrase, word_phrase, word, word_phrase_queue  where word_phrase_queue.word_id = word_phrase.word_id and not word_is_seen and phrase_is_pivotal and phrase.phrase_id = word_phrase.phrase_id and word_phrase.word_id = word.word_id group by phrase.phrase_id ;
                    )
            })

        def answers(s: Picture): List[Word] = picture2words.feed(s)
    }

    implicit object word2descriptions extends Join[Word, Description] with Order[Word, Description] {
        def feed(s: Word) = db.query("select description.description_id, description_value from description, word_description where word_id=? and word_description.description_id = description.description_id",
            x => new Description(x.columnInt(0)) { override val value = x.columnString(1) },
            s.id).toList

        def queue_number() = db.query("SELECT queue_number FROM exercise WHERE exercise_name='word_desription_queue'", _.columnDouble(0)).head
        def queue_number(number: Double) = { db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='word_description_queue'", number); number }

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='word_description_queue'""")

        def register(word: Word) = {
            incq()
            db.update("""
               INSERT INTO word_description_queue (word_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='word_description_queue'))
               """, word.id)
            word
        }

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE word_description_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_description_queue') 
                WHERE word_id=?""", word.id)

            new Convertors().description2words.incq()
        }

        def ahead(description: Description) = {
            incq()
            db.update("""
                UPDATE word_description_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='word_description_queue') 
                WHERE description_id in (SELECT word_id FROM word_description WHERE description_id=?) """, description.id)
        }

        def statistic() = db.query(
            "select (select count(*) from word_description_queue), (select count(*) from word_description_queue where date is not null), (select count(*) from word_description_queue where date > -exercise_begin), exercise_name, exercise_grade, strftime('%s','now')-exercise_begin from exercise where exercise_id = 3",
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=3", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=3")

        def update(r: Record[Word, Description]) = db.update("update word_description_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where word_description_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Word, Description], disable: Boolean) = {}

        def disable(r: Record[Word, Description], t: Description, disable: Boolean) = {}

        def disable(s: Word, t: Description, disable: Boolean) = db.update("update word_description set word_description_disabled = ? where word_id = ? and description_id = ?", disable, s.id, t.id)

        def query: Stream[Record[Word, Description]] = db.query("""
            select distinct word_description_queue_id, word.word_id, word_value, queue_weight, queue_number, date
            from word_description_queue 
            join word using(word_id) 
            join word_description using (word_id)
            where not word_is_seen and (word_status in (2,3)) order by queue_number""",
            x => new Record[Word, Description](
                x.columnInt(0),
                new Word(x.columnInt(1)) {
                    printf("=> %s %s %s\n", x.columnString(2), x.columnDouble(3), x.columnDouble(4))
                    val _value = x.columnString(2)
                    override lazy val value = _value
                },
                x.columnDouble(3),
                x.columnDouble(4),
                x.columnInt(5)))

        def answers(s: Word): List[Description] = s.descriptions
    }

    implicit object description2words extends Join[Description, Word] with Order[Description, Word] {
        def feed(s: Description) = db.query("select word.word_id, word_value from word, word_description where description_id=? and word_description.word_id = word.word_id",
            x => new Word(x.columnInt(0)) {
                val _value = x.columnString(1)
                override lazy val value = _value
            },
            s.id).toList

        def incq() = db.update("""UPDATE exercise SET queue_number = queue_number-1 WHERE exercise_name='desription_word_queue'""")
        def queue_number() = db.query("SELECT queue_number FROM exercise WHERE exercise_name='description_word_queue'", _.columnDouble(0)).head
        def queue_number(number: Double) = { db.update("UPDATE exercise SET queue_number=? WHERE exercise_name='description_word_queue'", number); number }

        def register(description: Description) = {
            incq()
            db.update("""
               INSERT INTO description_word_queue (description_id, queue_number) 
               VALUES (?, (SELECT queue_number FROM exercise WHERE exercise_name='description_word_queue'))
               """, description.id)
            description
        }

        def ahead(description: Description) = {
            incq()
            db.update("""
                UPDATE description_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='description_word_queue') 
                WHERE description_id=?""", description.id)
        }

        def ahead(word: Word) = {
            incq()
            db.update("""
                UPDATE description_word_queue 
                SET queue_number = (SELECT queue_number FROM exercise WHERE exercise_name='description_word_queue') 
                WHERE description_id in (SELECT description_id FROM word_description WHERE word_id=?) """, word.id)
        }

        def statistic() = db.query(
            "select (select count(*) from description_word_queue), (select count(*) from description_word_queue where date is not null), (select count(*) from description_word_queue where date > exercise_begin), exercise_name, exercise_grade, strftime('%s','now')-exercise_begin from exercise where exercise_id = 6",
            x => (x.columnInt(0), x.columnInt(1), x.columnInt(2), x.columnString(3), x.columnDouble(4), x.columnInt(5)))

        def statistic_grade(x: Double) = db.update("update exercise set exercise_grade=? where exercise_id=6", x)

        def statistic_begin() = db.update("update exercise set exercise_begin=strftime('%s','now') where exercise_id=6")

        def update(r: Record[Description, Word]) = db.update("update description_word_queue set queue_weight = ?, queue_number = ?, date=strftime('%s','now') where description_word_queue_id=?", r.weight, r.number, r.id)

        def disable(r: Record[Description, Word], disable: Boolean) = {}

        def disable(r: Record[Description, Word], t: Word, disable: Boolean) = {}

        def disable(s: Description, t: Word, disable: Boolean) = db.update("update word_description set word_description_disabled = ? where description_id = ? and word_id = ?", disable, s.id, t.id)

        def query: Stream[Record[Description, Word]] = db.query("select distinct description_word_queue_id, description.description_id, description_value, queue_weight, queue_number, date from description_word_queue join description using (description_id) join word_description using (description_id) join word using(word_id) where  not word_is_seen order by queue_number",
            x => new Record[Description, Word](
                x.columnInt(0),
                new Description(x.columnInt(1)) {
                    override val value = x.columnString(2)
                },
                x.columnDouble(3),
                x.columnDouble(4),
                x.columnInt(5)))

        def answers(s: Description): List[Word] = description2words.feed(s)
    }

}

class Record[S <: Field, T <: Field](val id: Int, val question: S, val weight: Double, val number: Double, val date: Int = 0)(implicit ord: Order[S, T], db: DB) {
    def toss(delta: Int) = {
        val w = weight * Math.pow(4, delta)
        val n = number + 1 + (Random.nextFloat * w)
        val r = new Record[S, T](id, question, w, n, 1)
        ord.queue_number(number)
        ord.update(r)
        r
    }

    def renum(number: Double) = { val r = new Record(id, question, weight, number, date); ord.update(r); r }

    def answers = ord.answers(question)

    def disable(disable: Boolean) = ord.disable(this, disable)

    def disable(t: T, disable: Boolean) = ord.disable(this, t, disable)

    def print = {
        log(" = Record # %s", number)
        question.print
        for (answer <- answers.take(5)) {
            answer.print
        }
        println(" ========")
    }

    override def toString = "Record # %s(%s date = %d)".format(id, question, date)

}

class Queue[S <: Field, T <: Field](implicit ord: Order[S, T]) {
    var queue = new PriorityQueue[Record[S, T]]()(Ordering.fromLessThan((x: Record[S, T], y: Record[S, T]) => x.number > y.number))

    def size = queue.size
    var (whole_size, unique, session, name, grade, endurance) = (0, 0, 0, "", 0.0, 0)

    type Result = (Int, Int, Int, String, Double, Int)

    load

    new AsyncTask[AnyRef, Unit, Result] {
        override def doInBackground(urls: AnyRef*): Result = {
            //Thread.sleep(2000)
            ord.statistic().headOption getOrElse ((0, 0, 0, "Unknown", 0.0, 0))
        }

        override def onPostExecute(result: Result) = {
            log("ready!!")
            var (_whole_size, _unique, _session, _name, _grade, _endurance) = result
            whole_size = whole_size + _whole_size
            unique = unique + _unique
            session = session + _session
            name = _name
            grade = grade + _grade
            endurance = endurance + _endurance
        }
    }.execute("")

    def setGrade(x: Double) = ord.statistic_grade(x)

    def setBegin() = ord.statistic_begin()

    def disable(disable: Boolean = true) = { queue.dequeue.disable(disable); this }

    def reload() = { queue.clear(); this }

    def postpone() = { load.dequeue; this }

    def toss(delta: Integer) = (if (queue.size == 0) load else {
        printf("== %s %s\n", queue.head.weight, queue.head.number)
        grade = grade * 0.993 + (if (delta > 0) 0.035 else 0)

        setGrade(grade)

        val r = queue.dequeue.toss(delta)

        printf("== %s %s\n", r.weight, r.number)

        (queue.lastOption) match {
            case Some(rl) if (rl.number > r.number) => queue += r
            case _                                  => {}
        }
        load
        printf("Number: %f\n", queue.head.number)
        log("r=%s date=%s", queue.head, queue.head.date)
        if (queue.head.date == 0) {
            log("Increment u=%d", unique)
            unique = unique + 1
            session = session + 1
        }
        load
    }).headOption

    def rit = toss(1)

    def ron = toss(-1)

    def renum(rs: Stream[Record[S, T]]) = {
        def renum(rs: Stream[Record[S, T]], rsr: Stream[Record[S, T]], n: Double): Stream[Record[S, T]] = rs match {
            case r #:: rs => renum(rs, r.renum(n - 1d) #:: rsr, n - 1d)
            case _        => rsr
        }

        rs. /*map(x => {x.print; x }).*/ reverse match {
            case r #:: rs => renum(rs, r #:: Stream[Record[S, T]](), r.number).map(x => { /*x.print;*/ x })
            case x        => x
        }

    }

    def load = {
        if (queue.size == 0) {
            queue ++= renum(ord.query.take(75))
        }
        queue
    }

    def head = load.head

    def headOption = load.headOption
}

