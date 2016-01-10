package ru.wordmetrix.dreamcrammer

import java.io._
import android.content.{Context, Intent}

import ru.wordmetrix.dreamcrammer.db._
import ru.wordmetrix._

import android.view.{Menu, MenuItem, View, ViewGroup, LayoutInflater}
import android.widget.{ToggleButton, TextView, Button, CompoundButton, ImageView, ArrayAdapter, ListView, PopupMenu, AdapterView, GridView}
import android.graphics.{BitmapFactory,Bitmap}
import android.support.v4.util.LruCache


abstract class BaseDisplay(context : DreamCrammerBase) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

    def inflate(resource : Int) : ViewGroup = inflater.inflate(resource, null).asInstanceOf[ViewGroup]

    def item(resource : Int) : ViewGroup = item(inflate(resource))
    def item(viewgroup : ViewGroup) : ViewGroup

    def view(resource : Int) : ViewGroup  = view(inflate(resource))
    def view(viewgroup : ViewGroup) : ViewGroup = item(viewgroup)

    def whole(resource : Int) : ViewGroup = whole(inflate(resource))
    def whole(viewgroup : ViewGroup) : ViewGroup = view(viewgroup)

    def edit(resource : Int) : ViewGroup = edit(inflate(resource))
    def edit(viewgroup : ViewGroup) : ViewGroup = whole(viewgroup)
}

