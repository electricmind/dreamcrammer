package ru.wordmetrix.dreamcrammer

import android.content.{SharedPreferences, Context}
// ticket : Rename to LexicalSoup

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._





abstract class PreferenceBase(val name : String)

class PreferenceInt(name : String, val default : Int = 0)(preferences : => SharedPreferences) extends PreferenceBase(name) {

    def apply(x : Int) = {
        log("Set %s = %s", name, x)
        x
    }

    def apply() = preferences.getInt(name, default)
}

class PreferenceBoolean(name : String, val default : Boolean = false)(preferences : => SharedPreferences) extends PreferenceBase(name) {

    def apply(x : Boolean) = {
        log("Set %s = %s", name, x)
        x
    }

    def apply() = preferences.getBoolean(name, default)
}

class PreferenceString(name : String, val default : String = "")(preferences : => SharedPreferences) extends PreferenceBase(name) {

    def apply(x : String) = {
        log("Set %s = %s", name, x)
        x
    }

    def apply() = preferences.getString(name, default)
}

class PreferencesBase(name : String, context : Context) {
    lazy val preferences = context.getSharedPreferences(name, 0)

    val doplay = new PreferenceBoolean("doplay", false)(preferences)

    val database = new PreferenceString("database", "taylor.db")(preferences)

    val layout = new PreferenceInt("layout", R.layout.main)(preferences) {
       
        override
        def apply() : Int = R.layout.main
    }
}

class PreferencesQuestionaire(name : String, context : Context) extends PreferencesBase(name, context) {

    val length = new PreferenceInt("questionaire_length", 75)(preferences)

    override
    val layout = new PreferenceInt("layout", R.layout.main)(preferences) {
        val layout = new PreferenceString("layout", "debug")(preferences)

        override
        def apply(x : Int) = {
            log("Set %s = %s", name, x)
            x
        }

        override
        def apply() : Int = { log("layout :( %s", layout.apply() ); layout.apply() match {
	    case "main" => R.layout.questionaire
            case "debug" => R.layout.questionaire_debug
            case _ => R.layout.questionaire
        }}
    }
}