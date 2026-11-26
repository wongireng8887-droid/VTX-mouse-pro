package com.vtx.mousepro.core

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Jembatan ke Shizuku: status, minta izin, dan membuka proses shell. */
object ShizukuBridge {
    const val PKG = "moe.shizuku.privileged.api"
    private const val REQ = 4711

    var onChange: (() -> Unit)? = null
    private var registered = false

    private val binderListener = Shizuku.OnBinderReceivedListener { onChange?.invoke() }
    private val deadListener = Shizuku.OnBinderDeadListener { onChange?.invoke() }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> onChange?.invoke() }

    fun init() {
        if (registered) return
        registered = true
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    private fun installed(c: Context): Boolean = try {
        c.packageManager.getPackageInfo(PKG, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (e: Exception) {
        false
    }

    /** 0 belum terpasang, 1 belum jalan, 2 perlu izin, 3 siap. */
    fun state(c: Context): Int {
        val alive = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        if (!alive) return if (installed(c)) 1 else 0
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
        return if (granted) 3 else 2
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQ)
        } catch (e: Throwable) {
        }
    }

    /** Proses shell lewat Shizuku (newProcess bersifat private di API 13, dipanggil lewat refleksi). */
    fun open(cmd: Array<String>): Injector.Conn {
        try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, cmd, null, null) as Process
            return Injector.Conn(p.inputStream, p.outputStream, p.errorStream) {
                try { p.destroy() } catch (e: Exception) {}
            }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw (e.targetException ?: e)
        }
    }
}
