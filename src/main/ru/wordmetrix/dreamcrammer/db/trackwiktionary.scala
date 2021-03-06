package ru.wordmetrix.dreamcrammer.db
import scala.annotation.tailrec
import java.io._
import java.net._

object TrackWiktionary extends Track {
    def get(url : String) : Option[Array[Byte]] = ("https://en.wiktionary.org/wiki/File:en-us-%s.ogg".format(url) :+: Page("""class="fullMedia"\s*>\s*<a\s+href="([^"]*)"""".r, x=>"https:" + x.subgroups(0))) :+: Data()
}

