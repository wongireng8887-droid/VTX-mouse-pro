package com.vtx.mousepro.core

import android.content.Context
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * Menjalankan InjectMain sebagai shell (lewat Shizuku) dan mengirim sentuhan ke sana.
 * Penulisan ke shell dilakukan satu thread pekerja yang tidak pernah keluar sendiri, jadi antrean tidak bisa macet.
 */
object Injector {
    class Conn(val input: InputStream, val output: OutputStream, val err: InputStream?, val close: () -> Unit)

    @Volatile var ready = false
    @Volatile var mode = ""
    @Volatile var error = ""
    @Volatile var connecting = false
    var onChange: (() -> Unit)? = null

    private var conn: Conn? = null
    private val lock = Any()

    private data class TouchCommand(val action: Int, val id: Int, val x: Float = 0f, val y: Float = 0f)

    private val qLock = ReentrantLock()
    private val qCond: Condition = qLock.newCondition()
    private val touchQueue = ArrayDeque<TouchCommand>()
    @Volatile private var lastProgress = 0L
    private val workerLock = Any()
    private var worker: Thread? = null

    private fun ensureWorker() {
        synchronized(workerLock) {
            val existing = worker
            if (existing != null && existing.isAlive) return
            val t = Thread({ workerLoop() }, "VtxTouchQueue")
            t.isDaemon = true
            worker = t
            t.start()
        }
    }

    private fun clearQueue() {
        qLock.lock()
        try { touchQueue.clear() } finally { qLock.unlock() }
    }

    private fun workerLoop() {
        while (true) {
            val batch = ArrayList<TouchCommand>(16)
            qLock.lock()
            try {
                while (touchQueue.isEmpty()) qCond.await()
                while (touchQueue.isNotEmpty() && batch.size < 24) batch.add(touchQueue.removeFirst())
            } catch (e: InterruptedException) {
                return
            } finally {
                qLock.unlock()
            }
            if (batch.isEmpty()) continue
            try {
                val sb = StringBuilder()
                for (cmd in batch) {
                    when (cmd.action) {
                        0 -> sb.append("D ").append(cmd.id).append(' ').append(Math.round(cmd.x)).append(' ').append(Math.round(cmd.y))
                        1 -> sb.append("M ").append(cmd.id).append(' ').append(Math.round(cmd.x)).append(' ').append(Math.round(cmd.y))
                        2 -> sb.append("U ").append(cmd.id)
                        else -> sb.append("R")
                    }
                    sb.append('\n')
                }
                val bytes = sb.toString().toByteArray()
                val wrote = synchronized(lock) {
                    val out = conn?.output
                    if (out == null || !ready) {
                        false
                    } else {
                        out.write(bytes)
                        out.flush()
                        true
                    }
                }
                if (!wrote) clearQueue() else lastProgress = SystemClock.uptimeMillis()
            } catch (e: Exception) {
                ready = false
                changed()
                clearQueue()
            } catch (t: Throwable) {
                clearQueue()
            }
        }
    }

    private fun cmdLine(ctx: Context): String =
        "CLASSPATH=" + ctx.applicationInfo.sourceDir +
            " exec app_process /system/bin com.vtx.mousepro.core.InjectMain " + ctx.packageName

    private fun changed() {
        onChange?.invoke()
    }

    /** Sambung lewat Shizuku. done(null) = sukses, selain itu pesan error (dipanggil di thread latar). */
    fun connect(ctx: Context, done: ((String?) -> Unit)?) {
        if (connecting) {
            done?.invoke("Sedang menyambung...")
            return
        }
        connecting = true
        error = ""
        changed()
        val app = ctx.applicationContext
        Thread {
            var err: String? = null
            try {
                stop()
                val c = ShizukuBridge.open(arrayOf("sh", "-c", cmdLine(app)))
                conn = c
                val latch = CountDownLatch(1)
                val errText = StringBuilder()
                val es = c.err
                if (es != null) {
                    Thread {
                        try {
                            BufferedReader(InputStreamReader(es)).forEachLine {
                                if (errText.length < 400) errText.append(it).append(' ')
                            }
                        } catch (e: Exception) {
                        }
                    }.start()
                }
                Thread {
                    try {
                        BufferedReader(InputStreamReader(c.input)).forEachLine { l ->
                            if (l == "READY") {
                                mode = "Shizuku"
                                ready = true
                                latch.countDown()
                                changed()
                            } else if (l.startsWith("ERR")) {
                                error = l
                            }
                        }
                    } catch (e: Exception) {
                    }
                    ready = false
                    latch.countDown()
                    changed()
                }.start()
                latch.await(10, TimeUnit.SECONDS)
                if (!ready) {
                    err = if (errText.isNotEmpty()) errText.toString()
                    else if (error.isNotEmpty()) error
                    else "Proses injeksi tidak mulai"
                }
            } catch (e: Throwable) {
                err = (e.cause ?: e).toString()
            }
            if (err != null) {
                error = err
                stop()
            }
            connecting = false
            changed()
            done?.invoke(err)
        }.start()
    }

    /** Mengantre satu perintah sentuh. action: 0 DOWN, 1 MOVE, 2 UP. */
    fun touch(action: Int, id: Int, x: Float, y: Float): Boolean {
        if (!ready || conn?.output == null) return false
        qLock.lock()
        try {
            if (action == 1) {
                // buang MOVE lama untuk jari yang sama setelah DOWN/UP terakhir; cukup posisi terbaru
                val iter = touchQueue.descendingIterator()
                while (iter.hasNext()) {
                    val c = iter.next()
                    if (c.action == 3 || (c.id == id && c.action != 1)) break
                    if (c.id == id && c.action == 1) iter.remove()
                }
            }
            if (touchQueue.isEmpty()) lastProgress = SystemClock.uptimeMillis()
            touchQueue.addLast(TouchCommand(action, id, x, y))
            qCond.signal()
        } finally {
            qLock.unlock()
        }
        ensureWorker()
        return true
    }

    fun releaseAll() {
        if (!ready || conn?.output == null) return
        qLock.lock()
        try {
            touchQueue.clear()
            touchQueue.addLast(TouchCommand(3, 0))
            qCond.signal()
        } finally {
            qLock.unlock()
        }
        ensureWorker()
    }

    fun queued(): Int {
        qLock.lock()
        try { return touchQueue.size } finally { qLock.unlock() }
    }

    /** Antrean berisi tetapi tidak ada kemajuan > 0,8 detik = penulisan ke shell macet. */
    fun stuck(): Boolean = ready && queued() > 0 && SystemClock.uptimeMillis() - lastProgress > 800

    fun restart(ctx: Context) {
        stop()
        connect(ctx, null)
    }

    fun stop() {
        ready = false
        clearQueue()
        val c = conn
        conn = null
        if (c != null) {
            // Q ditulis di thread terpisah: jika penulis lain tersangkut, jangan ikut macet.
            val w = Thread {
                try {
                    synchronized(lock) {
                        c.output.write("Q\n".toByteArray())
                        c.output.flush()
                    }
                } catch (e: Exception) {
                }
            }
            w.isDaemon = true
            w.start()
            try { w.join(300) } catch (e: InterruptedException) {}
            try { c.close() } catch (e: Exception) {}
        }
    }
}
