import ru.wordmetrix.dreamcrammer.db.PronunciationDictionary


object pronunciation {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  1                                               //> res0: Int(1) = 1
  
  
  
  PronunciationDictionary("lives").get            //> res1: String = laɪvz
  PronunciationDictionary("lesions").get          //> res2: String = ˈliʒən
  
   
}