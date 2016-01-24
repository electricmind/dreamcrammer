package ru.wordmetrix.dreamcrammer

import android.content.{BroadcastReceiver, ComponentName, Context, Intent}
import android.view.KeyEvent
import ru.wordmetrix.dreamcrammer.PlayerService.{PlayerServiceMessagePause, PlayerServiceMessageResume}
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

  override def onReceive(context: Context, intent: Intent) {
    println(s"RemoteControlReceiver $intent")
    println(s"RemoteControlReceiver ${intent.getAction()}")
    intent.getAction() match {
      case Intent.ACTION_MEDIA_BUTTON =>
        intent.getParcelableExtra[KeyEvent](Intent.EXTRA_KEY_EVENT).getKeyCode match {
          case KeyEvent.KEYCODE_MEDIA_PLAY =>
            log(s"RemoteControlReceiver should play")
            context.startService(new Intent(context, classOf[PlayerService]) {
              putExtra("message", PlayerServiceMessageResume)
            })

          case KeyEvent.KEYCODE_MEDIA_PAUSE =>
            log(s"RemoteControlReceiver should pause")
            context.startService(new Intent(context, classOf[PlayerService]) {
              putExtra("message", PlayerServiceMessagePause)
            })

          case code =>
            log(s"RemoteControlReceiver gets $code which is unknown")
        }
      case action =>
        log(s"Unknown action $action")
    }
  }
}
