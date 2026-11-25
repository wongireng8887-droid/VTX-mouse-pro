package com.vtx.mousepro

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/** Info perangkat yang dibaca langsung dari sistem. */
object DeviceInfo {
    fun model(): String = Build.MODEL ?: "-"

    fun android(): String = Build.VERSION.RELEASE ?: "-"

    fun arch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "-"
        val bit = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64-bit" else "32-bit"
        return "$abi ($bit)"
    }

    /** RAM dibulatkan ke ukuran pasar terdekat (sistem melaporkan sedikit di bawah angka di kotak). */
    fun ram(c: Context): String {
        val am = c.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val gib = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        val sizes = listOf(2, 3, 4, 6, 8, 10, 12, 16, 18, 24, 32)
        val pick = sizes.firstOrNull { it >= gib - 0.05 } ?: gib.toInt()
        return "$pick GB"
    }

    /** Penyimpanan dibulatkan ke ukuran pasar terdekat. */
    fun storage(): String {
        val total = StatFs(Environment.getDataDirectory().path).totalBytes
        val gb = total / 1_000_000_000.0
        val sizes = listOf(32, 64, 128, 256, 512, 1024, 2048)
        val pick = sizes.firstOrNull { it >= gb - 0.5 } ?: gb.toInt()
        return if (pick >= 1024) "${pick / 1024} TB" else "$pick GB"
    }
}
