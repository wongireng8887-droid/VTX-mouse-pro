package com.vtx.mousepro

import android.content.Context

/** Penyimpanan sederhana: daftar game dan game yang dipilih. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("vtx", Context.MODE_PRIVATE)

    fun games(c: Context): List<String> =
        (sp(c).getString("games", "") ?: "").split("|").filter { it.isNotEmpty() }

    fun saveGames(c: Context, list: List<String>) {
        sp(c).edit().putString("games", list.joinToString("|")).apply()
    }

    fun selected(c: Context): String = sp(c).getString("selected", "") ?: ""

    fun setSelected(c: Context, pkg: String) {
        sp(c).edit().putString("selected", pkg).apply()
    }
}
