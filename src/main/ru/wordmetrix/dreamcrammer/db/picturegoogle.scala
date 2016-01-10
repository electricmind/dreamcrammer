package ru.wordmetrix.dreamcrammer.db
import ru.wordmetrix._
import java.io._
import java.net._
import scala.util.matching.Regex.Match
import scala.util.matching.Regex

object ImgGoogle {
 
  def sleep(i : Int = 10000) {
      val ir = scala.util.Random.nextInt(i)
      printf("Sleep %d miliseconds\n",ir)
      Thread.sleep(ir)
  }

  def get(word : String, amount : Int) = {
    //val rUrl =  new Regex("""<img[^>]+height=[^>]+width=[^>]+src="([^"]*)"[^>]*>""")
    val rUrl =  new Regex("""<img[^>]+height=[^>]+src="([^"]*)"[^>]*width=[^>]+>""")
    //  val rUrl =  new Regex("""<img[^>]+class="rg_i"[^>]+src="([^"]*)"[^>]*>""")
     val root = new File("cache")
     //           https://www.google.ru/search?tbm=isch&amp;ie=windows-1251&amp;hl=ru&amp;source=hp&amp;q=ireland&amp;gbv=1
     val ref = """https://www.google.ru/search?tbm=isch&ie=windows-1251&hl=ru&source=hp&q=%s&gbv=1""".format(word.replace(" ","%20"))

     lazy val page = try {
       sleep()
       val connection = new URL(ref).openConnection() 
       connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux i686) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/21.0.1180.89 Safari/537.8")
       connection.setRequestProperty("Referer", "http://www.google.ru/imghp?hl=ru&tab=wi")
       val page = io.Source.fromInputStream(connection.getInputStream).mkString("")
//       log("page = %s",page)
       page
     } catch {
       case x : Throwable => { println("Word %s: %s".format(word,x)); throw(x); "" } 
     }

     lazy val pictures : Stream[String] = rUrl.findAllIn(page).matchData.map(_.subgroups(0)).toStream

     
     /*val f = new PrintStream(new FileOutputStream(s"/tmp/page-$word.txt"))
     f.print(page)
     f.close()
    */
     //println(page)

     for {
       i <- 0 until amount toStream
     } yield {
       val fn = new File(root, "img-%s-%d.jpg".format(word,i))
       println(word,i)

       try {
           val f = new FileInputStream(fn)
           val buf = new Array[Byte](f.available)
           f.read(buf)
           f.close()
           println("cache")
           Some(buf)
       } catch {
           case x : Throwable => {
               log("qq1")
               log("cache failed %s",x,word)
               sleep(1000)
               pictures.drop(i).headOption match {
                   case Some(x) => try {
                       val connection = (new URL(x)).openConnection()
                       connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux i686) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/21.0.1180.89 Safari/537.8")
                       connection.setRequestProperty("Referer", ref)
                       val st = connection.getInputStream
                       var count = 2

//                       log("word: %s - %s",word,x)

                       val buf = Stream.continually({
                           val buf = new Array[Byte](1024*1024)
                           val size = st.read(buf)
                           count = count + size
                           log("Read " + size + " " + count)
                           log("result :%s",buf.slice(1,20))
                           (buf.slice(0,size),size)
                       }).takeWhile({case (buf,size) =>  -1 != size}).map(_._1).toArray.flatten

                       /*val f = new FileOutputStream(fn)
                       f.write(buf)
                       f.close()*/
                       Some(buf)
                   } catch {
                       case x : Throwable => { println(x); throw(x); None }
                   }
                   case None => { log("can't find an image %s %s",word, i); None}
               }
           }
       }           
     }
//     log("Type QQ=%s", qq)
  //   qq
  }
}

object sqlpicture {
    def apply(implicit db : DB) = {
        for {
            word <- db.query("""select word_id, word_value from word where (select count(*) from word_picture where word_picture.word_id = word.word_id) == 0 and word_frequency > 0""",
            x => new Word(x.columnInt(0)) { 
                 val _value = x.columnString(1)
                 override lazy val value = _value 
            })
            picture <- ImgGoogle.get(word.value,2).flatten
        } { 
            word.addPicture(picture)
        }
    }
}
