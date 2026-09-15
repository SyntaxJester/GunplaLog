package com.syntaxjester.gunplalog

import android.content.Context
import org.json.JSONArray

class Store(context: Context) {

    val prefs = context.getSharedPreferences("gunplalog", Context.MODE_PRIVATE)

    fun load(): MutableList<Item> {
        val out = mutableListOf<Item>()
        try {
            val raw = prefs.getString("items", null) ?: return out
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                out.add(Item.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun save(items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
