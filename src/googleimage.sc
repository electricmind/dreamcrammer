import ru.wordmetrix.dreamcrammer.db.ImgGoogle

object googleimage {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  ImgGoogle.get("alias",2)                        //> Sleep 8130 miliseconds
                                                  //| (alias,0)
                                                  //| ?V-dreamcrammer-qq1
                                                  //| ?W-dreamcrammer-(java.io.FileNotFoundException: cache/img-alias-0.jpg (No su
                                                  //| ch file or directory))-cache failed alias
                                                  //| Sleep 859 miliseconds
                                                  //| Stream(https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcT0FaxB-az63nJeA
                                                  //| WvP3b8zRL0qNZswZsU04bSpH-qMYpK66ArswQ_oKQ, ?)
                                                  //| ?V-dreamcrammer-Read 1052 1054
                                                  //| ?V-dreamcrammer-result :[B@26a3960
                                                  //| ?V-dreamcrammer-Read 1393 2447
                                                  //| ?V-dreamcrammer-result :[B@4a6397eb
                                                  //| ?V-dreamcrammer-Read 1379 3826
                                                  //| ?V-dreamcrammer-result :[B@198e261d
                                                  //| ?V-dreamcrammer-Read -1 3825
                                                  //| ?V-dreamcrammer-result :[B@43684706
                                                  //| res0: scala.collection.immutable.Stream[Option[Array[Byte]]] = Stream(Some([
                                                  //| B@14be49e0), ?)
  
  
}