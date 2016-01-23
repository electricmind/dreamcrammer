package ru.wordmetrix.dreamcrammer

import android.app.PendingIntent
import android.content.{Context, Intent}
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v4.app.NotificationCompat.WearableExtender
import android.support.v4.app.{NotificationCompat, NotificationManagerCompat}
import android.support.v4.widget.{DrawerLayout, SlidingPaneLayout}
import android.view.{LayoutInflater, MenuItem, View, ViewGroup}
import android.widget.{ListView, PopupMenu, TextView, ToggleButton}
import ru.wordmetrix._
import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix.nlp.NLP.string2NLP
import android.support.v4.app.{NotificationCompat, NotificationManagerCompat}
import scala.util.Random

class Questionaire extends DreamCrammerBase with MenuQuestionaire {

  def doTurnOver() = {
    //ticket : Implement turn over
    log("this feature hasn't implemented yet")
  }

  override lazy val preferences = new PreferencesQuestionaire("DreamCrammer", this)

  override def layout = preferences.layout()

  import convertors._
                                                                                                              import android.support.v4.app.{NotificationCompat, NotificationManagerCompat}
  var queue: Option[Queue[Field, Field]] = None

  def doPostpone() = queue.exists(x => {
    publish(x.postpone()); true
  })

  def doReload() = queue.exists(x => {
    publish(x.reload()); true
  })

  def doDelete() = queue.exists(x => {
    publish(x.disable(true)); true
  })

  def queryPlayerIds() = db.query(
    """
                    select distinct word_id,word_value
                    from picture_word_queue
                        join picture using(picture_id)
                        join word_picture using(picture_id)
                        join word using(word_id)
                    where not word_is_seen
                        and word_track is not null
                        and not word_value like "% %"
                        and length(word_value) > 3
                    order by queue_number
                    limit 900
    """,
    x => x.columnInt(0)).toArray

  override def doPlayer = {
    startActivity(new Intent(this, classOf[Player]) {
      putExtra("word_ids", queryPlayerIds())
    })
  }

  def onToggleMenu(view: View) = {
    //findViewById(R.id.exercises).setVisibility(if (view.asInstanceOf[ToggleButton].isChecked) View.GONE else View.VISIBLE)

    val drawer = findViewById(R.id.queuesdrawer).asInstanceOf[DrawerLayout]
    val exercises = findViewById(R.id.exercises)
    if (view.asInstanceOf[ToggleButton].isChecked) drawer.openDrawer(exercises) else drawer.closeDrawer(exercises)
  }

  def onToggleAnswer(view: View): Unit = {
    //findViewById(R.id.answer).setVisibility(if (!view.asInstanceOf[ToggleButton].isChecked) View.INVISIBLE else View.VISIBLE)
    val panel = findViewById(R.id.answerslide).asInstanceOf[SlidingPaneLayout]
    if (view.asInstanceOf[ToggleButton].isChecked) panel.openPane() else panel.closePane()
  }

  def onClickAnswer(view: View): Unit = {
    log("onClickAnswer")
    val panel = findViewById(R.id.answerslide).asInstanceOf[SlidingPaneLayout]
    if (panel.isOpen) panel.closePane() else panel.openPane()
  }

  def saveKindOfQueue(kind_of_queue: Int) = {
    val editor = getSharedPreferences("DreamCrammer", 0).edit()
    editor.putInt("kind_of_queue", kind_of_queue)
    editor.commit()
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    getSharedPreferences("DreamCrammer", 0).getInt("kind_of_queue", 1) match {
      case 1 => onWordNPhrases(null)
      case 2 => onPhraseNWords(null)
      case 3 => onWordNPictures(null)
      case 4 => onPictureNWords(null)
      case 5 => onWordNDescriptions(null)
      case 6 => onDescriptionNWords(null)
    }

    findViewById(R.id.queuesdrawer).asInstanceOf[DrawerLayout].setDrawerListener(new DrawerLayout.SimpleDrawerListener() {
      val button = Questionaire.this.findViewById(R.id.togglequeues).asInstanceOf[ToggleButton]

      override def onDrawerClosed(drawerView: View): Unit = button.setChecked(false)

      override def onDrawerOpened(drawerView: View): Unit = button.setChecked(true)
    })

    findViewById(R.id.answerslide).asInstanceOf[SlidingPaneLayout].setPanelSlideListener(new SlidingPaneLayout.SimplePanelSlideListener() {
      val button = Questionaire.this.findViewById(R.id.toggleanswer).asInstanceOf[ToggleButton]

      override def onPanelClosed(view: View): Unit = button.setChecked(false)

      override def onPanelOpened(view: View): Unit = button.setChecked(true)
    })

    startService(new Intent(this, classOf[TaskService]) {})

    //notificationManager.notify(notifyId.next(), notificationBuilder.build())

    //        handler.postDelayed(new Runnable() {
    //            def run() {
    //                notificationManager.cancel(id)
    //            }
    //        }, 40000)

  }

  lazy val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(Questionaire.this)

  lazy val notifyId = Iterator.from(Random.nextInt / 2)

  def onPronounce(view: View = null) = {
    log("pronounce")
    for {
      queue <- queue
      x <- queue.headOption
    } {
      (for {
        tts <- textToSpeach
        value <- x.answers.headOption.collect{
          case p: Phrase =>
            val index = (System.currentTimeMillis() / (1000*60*60*24) % x.answers.length).toInt
            x.answers(index)
        }.collect{
          case p: Phrase =>
            p.value
        }
      } yield {
          tts.speak(value, TextToSpeech.QUEUE_FLUSH, null)
        }) orElse (x.answers collect {
        case w: Word => play(w)
      } headOption) getOrElse {
        x.question match {
          case w: Word => play(w)

          case p: Phrase =>
            textToSpeach foreach {
              case tts => tts.speak(p.value, TextToSpeech.QUEUE_FLUSH, null)
            }

          case _ =>
        }
      }
    }
  }

  def publish(queue: Queue[Field, Field]): Unit = {
    this.queue = Some(queue)
    queue.headOption match {
      case Some(x) => publish(x)
      case None => {}
    }
  }

  def publish(record: Record[Field, Field]): Unit = queue.exists(queue => {
    val record = queue.head
    record.question.print
    if (preferences.doplay()) onPronounce()

    findViewById(R.id.queuesdrawer).asInstanceOf[DrawerLayout].closeDrawer(findViewById(R.id.exercises))
    findViewById(R.id.answerslide).asInstanceOf[SlidingPaneLayout].closePane()

    findViewById(R.id.answer).setVisibility(View.INVISIBLE)
    findViewById(R.id.toggleanswer).asInstanceOf[ToggleButton].setChecked(false)
    findViewById(R.id.grade).asInstanceOf[TextView].setText("%4.2f".format(queue.grade))
    findViewById(R.id.size).asInstanceOf[TextView].setText("%d".format(queue.size))
    findViewById(R.id.unique).asInstanceOf[TextView].setText("%d".format(queue.unique))
    findViewById(R.id.weight).asInstanceOf[TextView].setText("%4.2f".format(record.weight))
    findViewById(R.id.number).asInstanceOf[TextView].setText("%4.2f".format(record.number))
    findViewById(R.id.whole_size).asInstanceOf[TextView].setText("%d".format(queue.whole_size))
    findViewById(R.id.session).asInstanceOf[TextView].setText("%d".format(queue.session))
    findViewById(R.id.endurance).asInstanceOf[TextView].setText("%d".format(queue.endurance))

    val inflater: LayoutInflater = this.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

    findViewById(R.id.question).asInstanceOf[ViewGroup].removeAllViews()
    findViewById(R.id.question).asInstanceOf[ViewGroup].addView(record.question match {
      case picture: Picture => display(inflater.inflate(R.layout.pictureview, null).asInstanceOf[ViewGroup], picture)

      case phrase: Phrase => {
        val viewGroup: ViewGroup = inflater.inflate(R.layout.phraseview, null).asInstanceOf[ViewGroup]
        val textview = viewGroup.findViewById(R.id.phrase_value).asInstanceOf[TextView]

        textview.setText(phrase.value.hidephrases(record.answers.map({
          case w: Word => Some(w.value);
          case _ => None
        }).flatten))

        textview.setOnClickListener(new View.OnClickListener {
          override def onClick(v: View) {
            startActivity(new Intent(Questionaire.this, classOf[Quotation]) {
              putExtra("phrase_id", phrase.id)
            })
          }
        })
        viewGroup
      }

      case description: Description => {
        val viewGroup: ViewGroup = inflater.inflate(R.layout.phraseview, null).asInstanceOf[ViewGroup]
        viewGroup.findViewById(R.id.phrase_value).asInstanceOf[TextView].setText(description.value)
        viewGroup
      }

      case word: Word => new WordDisplay(this, word, true).view(R.layout.wordview_sliding)
    })

    findViewById(R.id.answer).asInstanceOf[ViewGroup].removeAllViews()
    findViewById(R.id.answer).asInstanceOf[ViewGroup].addView(record.answers.head match {
      case word: Word => {
        val viewGroup: ViewGroup = inflater.inflate(R.layout.worditems, null).asInstanceOf[ViewGroup]
        for (word <- record.answers.asInstanceOf[List[Word]]) {
          viewGroup.findViewById(R.id.wordvalues).asInstanceOf[ViewGroup].addView(
            Exec(new WordDisplay(this, word, true).view(R.layout.worditem))(
              x => Exec(x.findViewById(R.id.word_value).asInstanceOf[TextView])(
                (x => x.setText(word.value)) /*,
                                 (x => x.setOnClickListener(new View.OnClickListener {
                                     override
                                     def onClick(v : View) = {
                                         startActivity(new Intent(Questionaire.this, classOf[Vocabulary]) {
                                             putExtra("word_id", word.id)
                                         })
                                     }
                                 }))*/).get).get)
        }
        viewGroup
      }

      case picture: Picture => {
        val viewGroup: ViewGroup = inflater.inflate(R.layout.pictureitems, null).asInstanceOf[ViewGroup]
        for ((picture, index) <- record.answers.asInstanceOf[List[Picture]].zipWithIndex) {
          val pictures = viewGroup.findViewById(R.id.picturevalues).asInstanceOf[ViewGroup]
          val pictureview = new PictureDisplay(this, picture).item().asInstanceOf[FrameMenu]

          pictureview.setOnClickListener(new View.OnClickListener {
            def onClick(view: View): Unit = {
              Questionaire.this.startActivity(new Intent(Questionaire.this, classOf[Gallery]) {
                putExtra("picture_id", picture.id)
              })
            }
          })
          pictureview.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener {
            def onMenuItemClick(item: MenuItem): Boolean = item.getItemId() match {
              case R.id.lookat => {
                Questionaire.this.startActivity(new Intent(Questionaire.this, classOf[Gallery]) {
                  putExtra("picture_id", picture.id)
                })
                false

              }

              case R.id.delete => {
                log("Delete item")
                record.disable(picture, true)
                pictures.removeView(pictureview)
                false
              }

              //case R.id.lookat => { log("Look at item"); false }

              case _ => log("yawn!"); false
            }
          })

          pictures.addView(pictureview)

        }
        viewGroup
      }

      case phrase: Phrase => {
        val viewGroup = inflater.inflate(R.layout.phraseitems, null).asInstanceOf[ListView]
        viewGroup.setAdapter(
          new PhraseListAdapter(this, R.layout.phraseitem, record.answers.asInstanceOf[List[Phrase]]))
        viewGroup

        /*                 val viewGroup : ViewGroup = inflater.inflate(R.layout.phraseitems, null).asInstanceOf[ViewGroup]


         for (phrase <- record.answers.asInstanceOf[List[Phrase]]) {
             viewGroup.findViewById(R.id.phrasevalues).asInstanceOf[ViewGroup].addView({
                 val viewGroup : ViewGroup = inflater.inflate(R.layout.phraseview, null).asInstanceOf[ViewGroup]
                 viewGroup.findViewById(R.id.phrasevalue).asInstanceOf[TextView].setText(phrase.value)
                 viewGroup
             })

         viewGroup */

      }

      case description: Description => {
        val viewGroup: ViewGroup = inflater.inflate(R.layout.descriptionitems, null).asInstanceOf[ViewGroup]
        for (description <- record.answers.asInstanceOf[List[Description]]) {
          viewGroup.findViewById(R.id.phrasevalues).asInstanceOf[ViewGroup].addView({
            val viewGroup: ViewGroup = inflater.inflate(R.layout.phraseview, null).asInstanceOf[ViewGroup]
            viewGroup.findViewById(R.id.phrase_value).asInstanceOf[TextView].setText(description.value)
            viewGroup
          })
        }
        viewGroup

      }
    })
    true
  })

  def onKnown(view: View): Unit = queue.map(_.rit.map(publish))

  def onUnknown(view: View): Unit = queue.map(_.ron.map(publish))

  def onWordNPhrases(view: View) = {
    log("Word and Phrases")
    saveKindOfQueue(1)
    publish(new Queue[Word, Phrase]().asInstanceOf[Queue[Field, Field]])
  }

  def onPhraseNWords(view: View) = {
    log("Word and Phrases")
    saveKindOfQueue(2)
    publish(new Queue[Phrase, Word]().asInstanceOf[Queue[Field, Field]])
  }

  def onWordNPictures(view: View) = {
    log("Word and Pictures")
    saveKindOfQueue(3)
    publish(new Queue[Word, Picture]().asInstanceOf[Queue[Field, Field]])
  }

  def onPictureNWords(view: View) = {
    log("Picture and Words")
    saveKindOfQueue(4)
    publish(new Queue[Picture, Word]().asInstanceOf[Queue[Field, Field]])
  }

  def onWordNDescriptions(view: View) = {
    log("Word and Descriptions")
    saveKindOfQueue(5)
    publish(new Queue[Word, Description]().asInstanceOf[Queue[Field, Field]])
  }

  def onDescriptionNWords(view: View) = {
    log("Description and Words")
    saveKindOfQueue(6)
    publish(new Queue[Description, Word]().asInstanceOf[Queue[Field, Field]])
  }

  def onPrepositions(view: View) = {
    log("Prepositions")
  }

  def onIdioms(view: View) = {
    log("Idioms")
  }

  def onUknown(view: View) = {
    log("UnKnown")
  }

  def onKnow(view: View) = {
    log("Known")
  }
}

	