package ru.wordmetrix.dreamcrammer

import android.content.{BroadcastReceiver, ComponentName, Context, Intent}
import android.view.KeyEvent
import ru.wordmetrix.dreamcrammer.PlayerService._
import ru.wordmetrix.log

/**
 * Created by cray on 1/24/16.
 */
object PlayerServiceRemoteControlReceiver {
  val ComponentName = new ComponentName(
    classOf[PlayerServiceRemoteControlReceiver].getPackage.getName(),
    classOf[PlayerServiceRemoteControlReceiver].getName())
}

class PlayerServiceRemoteControlReceiver extends BroadcastReceiver {
  log("RemoteControlReceiver started")

  private def send(context: Context, message: PlayerServiceMessage): Unit = {
    context.startService(new Intent(context, classOf[PlayerService]) {
      putExtra("message", message)
    })

  }
  override def onReceive(context: Context, intent: Intent) {
    println(s"RemoteControlReceiver $intent")
    println(s"RemoteControlReceiver ${intent.getAction()}")
    intent.getAction() match {
      case Intent.ACTION_MEDIA_BUTTON =>
        val event = intent.getParcelableExtra[KeyEvent](Intent.EXTRA_KEY_EVENT)

        if (event.getAction == KeyEvent.ACTION_DOWN) event.getKeyCode match {
          case KeyEvent.KEYCODE_MEDIA_PLAY =>
            log(s"RemoteControlReceiver should play")
            send(context, PlayerServiceMessagePhraseOfTheDay)

          case KeyEvent.KEYCODE_MEDIA_PAUSE =>
            log(s"RemoteControlReceiver should pause")
            send(context, PlayerServiceMessageViewLast)

          case KeyEvent.KEYCODE_MEDIA_NEXT =>
            log(s"RemoteControlReceiver should show next")
            send(context, PlayerServiceMessageViewNext)

          case KeyEvent.KEYCODE_MEDIA_PREVIOUS =>
            log(s"RemoteControlReceiver should show previous")
            send(context, PlayerServiceMessageViewPrevious)

          case code =>
            log(s"RemoteControlReceiver gets $code which is unknown")
        } else log(s"Event action = ${event.getAction}")

      case action =>
        log(s"Unknown action $action")
    }
  }
}
