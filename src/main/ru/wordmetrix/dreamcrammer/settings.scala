package ru.wordmetrix.dreamcrammer

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity

class Settings extends PreferenceActivity with SharedPreferences.OnSharedPreferenceChangeListener {
  override protected def onCreate(icicle: Bundle) {
    try {
       super.onCreate(icicle)
       getPreferenceManager.setSharedPreferencesName("DreamCrammer")
       addPreferencesFromResource(R.xml.preferences)
       getPreferenceManager.getSharedPreferences registerOnSharedPreferenceChangeListener this
    } catch {
       case x : Throwable => println(x)
    }
  }

  override protected def onResume() {
    super.onResume()
  }

  override protected def onDestroy() {
    getPreferenceManager.getSharedPreferences unregisterOnSharedPreferenceChangeListener this
    super.onDestroy()
  }

  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
  }
}
