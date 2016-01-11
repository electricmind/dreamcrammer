package ru.wordmetrix.dreamcrammer.db

import java.io._
import java.net._

object TrackDictionary extends Track {
    def get(url : String) : Option[Array[Byte]] = ("http://dictionary.reference.com/browse/%s".format(url) :+: Page("""a\s*class="speaker"\s*href="([^"]*)"""".r, x=>x.subgroups(0))) :+: Data()
}

