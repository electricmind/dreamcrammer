package ru.wordmetrix.dreamcrammer

import java.io._
import android.media.{MediaPlayer,AudioManager}
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._
import android.app.Activity
import android.content.Context

trait PlayerBase extends Context {
    def play(word : Word) = {
        word.track match {
            case SomeData(track) => stack {
                  try {
                      val name = "%s.ogg".format(word)
                      val tmp = openFileOutput(name, Context.MODE_PRIVATE)
                      tmp.write(track)
                      tmp.close
                      new FileInputStream(getFileStreamPath(name).getPath)
                      val mediaPlayer : MediaPlayer = new MediaPlayer();

                      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
                      mediaPlayer.reset()

                      mediaPlayer.setDataSource(new FileInputStream(getFileStreamPath(name).getPath).getFD())
                      mediaPlayer.prepare();
                      mediaPlayer.start();
                      Thread.sleep(mediaPlayer.getDuration());
                      mediaPlayer.release()
                  } catch {
                    case x : Throwable => log("Player fail",x)
                  }
            }
            case NoData | NeverData => {}
        }
   }
}

