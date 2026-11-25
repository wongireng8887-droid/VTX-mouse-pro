package com.vtx.mousepro.ui

/** Menggabungkan alfa (0..255) dan warna RGB (0xRRGGBB) menjadi warna Int ARGB. */
fun argb(a: Int, rgb: Int): Int = (a.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

/** Palet warna tema Liquid Glass. */
object Vtx {
    val BG0: Int = argb(255, 0x030A09)
    val BG1: Int = argb(255, 0x07201A)
    val MINT: Int = argb(255, 0x2DF0B8)
    val MINT_DEEP: Int = argb(255, 0x12A57F)
    val TEXT: Int = argb(255, 0xF2FFFB)
    val MUTED: Int = argb(255, 0x8FA8A0)
    val AMBER: Int = argb(255, 0xF5B73B)
    val RED: Int = argb(255, 0xFF6B6B)
}
