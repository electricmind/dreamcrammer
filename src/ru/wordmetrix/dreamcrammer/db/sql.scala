package ru.wordmetrix.dreamcrammer.db

import ru.wordmetrix._

object DB {
    abstract trait Access {
        def columnInt(num : Int) : Int
        def columnBoolean(num : Int) : Boolean = { log("boolean: %s %s",columnInt(num), columnInt(num) == 1); columnInt(num) == 1 }
        def columnString(num : Int) : String
        def columnDouble(num : Int) : Double
        def columnBlob(num : Int) : Array[Byte]
    }
}

abstract trait DB {
    def query[T](query : String, factory : DB.Access => T, args : Any*) : Stream[T]
    def update(query : String, args : Any*) : Unit
    def close() : Unit

    def getVersion() = getTargetVersion()

    def getTargetVersion() = 11

    def onUpgrade(update : String => Unit, oldVersion : Int, newVersion : Int) = {
        log("On upgrase", oldVersion, newVersion)

        for ( version <- oldVersion to newVersion ) version match {
        case 1 => {
            log("Upgrade version 1")
            """
            CREATE TABLE IF NOT EXISTS  exercise (exercise_id integer primary key autoincrement, exercise_name string unique default "", exercise_grade real default 0.0, exercise_begin integer default 0);
            INSERT INTO exercise (exercise_name) VALUES("Word 2 Phrase");
            INSERT INTO exercise (exercise_name) VALUES("Word 2 Picture");                                                                                                                                                                                                         
            INSERT INTO exercise (exercise_name) VALUES("Word 2 Description");                                                                                                                                                                                                     
            INSERT INTO exercise (exercise_name) VALUES("Phrase 2 Word");                                                                                                                                                                                                     
            INSERT INTO exercise (exercise_name) VALUES("Picture 2 Word");                                                                                                                                                                                                         
            INSERT INTO exercise (exercise_name) VALUES("Description 2 Word");                                                                                                                                                                                                     


            ALTER TABLE word ADD COLUMN word_date integer;

            ALTER TABLE word_phrase_queue ADD COLUMN date integer;                                                                                                                                                                                                                
            ALTER TABLE word_picture_queue ADD COLUMN date integer;                                                                                                                                                                                                               
            ALTER TABLE word_description_queue ADD COLUMN date integer;                                                                                                                                                                                                            
            ALTER TABLE phrase_word_queue ADD COLUMN date integer;                                                                                                                                                                                                            
            ALTER TABLE picture_word_queue ADD COLUMN date integer;                                                                                                                                                                                                                
            ALTER TABLE description_word_queue ADD COLUMN date integer;                                                                                                                                                                                                            

            ALTER TABLE word ADD COLUMN word_status integer default 3;                                                                                                                                                                                                  
            ALTER TABLE phrase ADD COLUMN phrase_is_pivotal boolean default false; 
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 2 => {
            log("Upgrade version 2")
            """create index hmm2_hmm2_word1_id_hmm2_word2_id on hmm2(hmm2_word1_id, hmm2_word2_id)""".split("\\s*;\\s*").map(x=>update(x))
        }

        case 3 => {
            log("Upgrade version 3")
            """
            CREATE TABLE IF NOT EXISTS task (task_id integer primary key autoincrement, task_date integer default 0, task_status integer not null, task_type integer not null, task_arg1 string, task_arg2 string, task_arg3 string);
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 4  => {
            log("Upgrade version 4")
            """
            DROP TABLE task;
            CREATE TABLE IF NOT EXISTS task (task_id integer primary key autoincrement, task_date integer default 0, task_status integer not null, task_kind integer not null, task_arg1 string, task_arg2 string, task_arg3 string);
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 5  => {
            log("Upgrade version 5")
            """
            ALTER TABLE exercise ADD COLUMN queue_number double default 0.0;
	    UPDATE exercise SET exercise_name = "word_phrase_queue" WHERE exercise_id=1;
	    UPDATE exercise SET exercise_name = "word_picture_queue" WHERE exercise_id=2;
	    UPDATE exercise SET exercise_name = "word_description_queue" WHERE exercise_id=3;
	    UPDATE exercise SET exercise_name = "phrase_word_queue" WHERE exercise_id=4;
	    UPDATE exercise SET exercise_name = "picture_word_queue" WHERE exercise_id=5;
	    UPDATE exercise SET exercise_name = "description_word_queue" WHERE exercise_id=6;
            ALTER TABLE picture ADD COLUMN picture_md5 string default "";
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 6  => {
            log("Upgrade version 6")
            """
            create index picture_picture_md5 on picture(picture_md5)
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 7  => {
            log("Upgrade version 7")
            """
            ALTER TABLE word ADD COLUMN word_track_status integer not null default 2;
            ALTER TABLE word ADD COLUMN word_ipa_status integer not null default 2;
            ALTER TABLE word ADD COLUMN word_lastseen integer not null default 0;
            ALTER TABLE task ADD COLUMN task_field integer;
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 8  => {
            log("Upgrade version 8")
            """
            UPDATE word SET word_track_status = 0 WHERE word_track is not null;
            UPDATE word SET word_ipa_status = 0 WHERE word_ipa is null or word_ipa = "";
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 9  => {
            log("Upgrade version 9")
            """
            UPDATE word SET word_ipa_status = 0;
            UPDATE word SET word_ipa_status = 2 WHERE word_ipa is null or word_ipa = "";
            """.split("\\s*;\\s*").map(x=>update(x))
        }

        case 10  => {
            log("Upgrade version 9")
            """
            ALTER TABLE task ADD COLUMN task_last integer default 0;
            """.split("\\s*;\\s*").map(x=>update(x))
        }
            
        case 11 => {
            log("We have completed upgrade!")
        }
      
    }}

/*    abstract trait Access {
        def columnInt(num : Int) : Int
        def columnBoolean(num : Int) : Boolean = { log("boolean: %s %s",columnInt(num), columnInt(num) == 1); columnInt(num) == 1 }
        def columnString(num : Int) : String
        def columnDouble(num : Int) : Double
        def columnBlob(num : Int) : Array[Byte]
    }*/

        //BEGIN TRANSACTION;
    val schema = """
        CREATE TABLE IF NOT EXISTS word (word_id integer primary key autoincrement, word_value string unique default "", word_is_seen boolean default false, word_frequency integer default 1, word_track blob, word_notrack boolean default false, word_ipa string);
        CREATE TABLE IF NOT EXISTS phrase (phrase_id integer primary key autoincrement, phrase_value string unique default "");
        CREATE TABLE IF NOT EXISTS picture (picture_id integer primary key autoincrement, picture_value string default "", picture_body blob);
        CREATE TABLE IF NOT EXISTS description (description_id integer primary key autoincrement, description_value string unique default "");

        CREATE TABLE IF NOT EXISTS word_phrase (word_phrase_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, phrase_id int references phrase (phrase_id) on update cascade on delete cascade);
        CREATE TABLE IF NOT EXISTS word_picture (word_picture_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, picture_id int references picture (picture_id) on update cascade on delete cascade);
        CREATE TABLE IF NOT EXISTS word_description (word_description_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, description_id int references description (description_id) on update cascade on delete cascade);

        CREATE TABLE IF NOT EXISTS word_phrase_queue (word_phrase_queue_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);
        CREATE TABLE IF NOT EXISTS word_description_queue (word_description_queue_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);
        CREATE TABLE IF NOT EXISTS word_picture_queue (word_picture_queue_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);

        CREATE TABLE IF NOT EXISTS phrase_word_queue (phrase_word_queue_id integer primary key autoincrement, phrase_id int references phrase (phrase_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);
        CREATE TABLE IF NOT EXISTS description_word_queue (description_word_queue_id integer primary key autoincrement, description_id int references description (description_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);
        CREATE TABLE IF NOT EXISTS picture_word_queue (picture_word_queue_id integer primary key autoincrement, picture_id int references picture (picture_id) on update cascade on delete cascade, queue_number double default 0.0, queue_weight double default 4.0);

        CREATE TABLE IF NOT EXISTS tag (tag_id integer primary key autoincrement, tag_value string default "");
        CREATE TABLE IF NOT EXISTS word_tag (word_tag_id integer primary key autoincrement, word_id int references word (word_id) on update cascade on delete cascade, tag_id int references tag (tag_id) on update cascade on delete cascade, word_tag_number double default 0.0, word_tag_weight double default 4.0);

        CREATE TABLE IF NOT EXISTS hmm1 (hmm1_id integer primary key autoincrement, hmm1_word1_id int references word (word_id) on update cascade on delete cascade, hmm1_emit_id int references word (word_id) on update cascade on delete cascade, hmm1_frequency integer default 1);
        CREATE TABLE IF NOT EXISTS hmm2 (hmm2_id integer primary key autoincrement, hmm2_word1_id int references word (word_id) on update cascade on delete cascade, hmm2_word2_id int references word (word_id) on update cascade on delete cascade, hmm2_emit_id int references word (word_id) on update cascade on delete cascade, hmm2_frequency integer default 1);

        CREATE UNIQUE INDEX IF NOT EXISTS index_hmm1 on hmm1 (hmm1_word1_id, hmm1_emit_id);
        CREATE UNIQUE INDEX IF NOT EXISTS index_hmm2 on hmm2 (hmm2_word1_id, hmm2_word2_id, hmm2_emit_id);
        
        """

        //COMMIT;"""

    def create = schema.split("\\s*;\\s*").map( x=>update(x) )

}

