package com.vtx.mousepro.core

import android.content.Context
import android.os.SystemClock

/**
 * Status koneksi gabungan untuk UI dan penyambungan OTOMATIS:
 * begitu Shizuku jalan, aplikasi minta izin sendiri (sekali per sesi) lalu menyambung sendiri.
 */
object Link {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NEED_PERMISSION, CONNECTING, CONNECTED, ERROR }

    private var askedThisSession = false
    @Volatile private var lastAuto = 0L

    fun state(c: Context): State {
        if (Injector.ready) return State.CONNECTED
        if (Injector.connecting) return State.CONNECTING
        return when (ShizukuBridge.state(c)) {
            0 -> State.NOT_INSTALLED
            1 -> State.NOT_RUNNING
            2 -> State.NEED_PERMISSION
            else -> if (Injector.error.isNotEmpty()) State.ERROR else State.CONNECTING
        }
    }

    /** Panggil saat aplikasi dibuka, saat Shizuku berubah, dan saat izin dijawab. */
    fun autoConnect(c: Context) {
        val app = c.applicationContext
        when (ShizukuBridge.state(app)) {
            3 -> {
                askedThisSession = true
                if (!Injector.ready && !Injector.connecting) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAuto > 3000) {
                        lastAuto = now
                        Injector.connect(app, null)
                    }
                }
            }
            2 -> {
                if (!askedThisSession) {
                    askedThisSession = true
                    ShizukuBridge.requestPermission()
                }
            }
            else -> askedThisSession = false
        }
    }

    /** Dipakai tombol "Coba lagi". */
    fun connectNow(c: Context) {
        lastAuto = SystemClock.elapsedRealtime()
        Injector.connect(c.applicationContext, null)
    }
}
