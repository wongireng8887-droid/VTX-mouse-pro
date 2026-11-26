package com.vtx.mousepro

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.vtx.mousepro.core.Injector
import com.vtx.mousepro.core.Link
import com.vtx.mousepro.core.ShizukuBridge
import com.vtx.mousepro.ui.GlassBackground
import com.vtx.mousepro.ui.GlassDrawable
import com.vtx.mousepro.ui.GlassToggle
import com.vtx.mousepro.ui.TouchTargetView
import com.vtx.mousepro.ui.Vtx
import com.vtx.mousepro.ui.argb

/**
 * VTX Mouse Pro - Fase 1 (fondasi).
 * Kerangka tampilan Liquid Glass dengan tiga tab: Game, Optimisasi Performa, Pengaturan.
 * Koneksi (Shizuku/ADB), overlay, dan pemetaan menyusul di fase berikutnya.
 */
class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var content: FrameLayout
    private lateinit var subtitle: TextView
    private lateinit var navTabs: List<LinearLayout>
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var mouseIcon: ImageView
    private lateinit var keyIcon: ImageView
    private lateinit var shizukuIcon: ImageView

    private var tab = 0
    private var settingsPage = 0
    private var showingSub = false
    private var returnTab = 0
    private var allApps: List<AppEntry> = emptyList()

    private class AppEntry(val pkg: String, val label: String, val icon: Drawable)

    private val backCb = OnBackInvokedCallback { closeSub() }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { updateStatus() }
        override fun onInputDeviceRemoved(deviceId: Int) { updateStatus() }
        override fun onInputDeviceChanged(deviceId: Int) { updateStatus() }
    }

    // ------------------------------------------------------------------ siklus hidup
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        ShizukuBridge.init()

        val root = FrameLayout(this)
        root.addView(GlassBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.addView(header(), lp(MATCH, WRAP))
        content = FrameLayout(this)
        column.addView(content, lp(MATCH, 0, 1f))
        column.addView(bottomNav(), lp(MATCH, WRAP))
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        root.setOnApplyWindowInsetsListener { _, insets ->
            val b = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            )
            column.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(root)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        select(0)

        // Perubahan Shizuku / koneksi -> sambung otomatis dan segarkan tampilan
        ShizukuBridge.onChange = {
            runOnUiThread {
                Link.autoConnect(applicationContext)
                refreshAll()
            }
        }
        Injector.onChange = { runOnUiThread { refreshAll() } }
    }

    override fun onDestroy() {
        ShizukuBridge.onChange = null
        Injector.onChange = null
        super.onDestroy()
    }

    private fun refreshAll() {
        updateStatus()
        if (!showingSub && tab == 2) select(2)
    }

    override fun onResume() {
        super.onResume()
        val im = getSystemService(Context.INPUT_SERVICE) as InputManager
        im.registerInputDeviceListener(inputListener, null)
        Link.autoConnect(this)
        updateStatus()
        if (!showingSub) select(tab)
    }

    override fun onPause() {
        super.onPause()
        val im = getSystemService(Context.INPUT_SERVICE) as InputManager
        im.unregisterInputDeviceListener(inputListener)
    }

    // ------------------------------------------------------------------ pembantu tampilan
    private val density: Float get() = resources.displayMetrics.density

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private fun lp(
        w: Int, h: Int, weight: Float = 0f,
        top: Int = 0, left: Int = 0, right: Int = 0, bottom: Int = 0
    ): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(left, top, right, bottom)
        return p
    }

    private fun glass(r: Int, strength: Float = 1f, glow: Boolean = false, filled: Boolean = false): GlassDrawable =
        GlassDrawable(r * density, density, strength, Vtx.MINT, glow, filled)

    private fun tv(s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.includeFontPadding = false
        return t
    }

    private fun icon(res: Int, tint: Int, sizeDp: Int): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(tint)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return v
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun vLine(alpha: Int = 45): View {
        val v = View(this)
        v.setBackgroundColor(argb(alpha, 0xFFFFFF))
        return v
    }

    // ------------------------------------------------------------------ header
    private fun header(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(20), dp(10), dp(20), dp(8))

        val logoTile = FrameLayout(this)
        logoTile.background = glass(14, 1.2f, glow = true)
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_vtx_logo)
        logoTile.addView(logo, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        row.addView(logoTile, lp(dp(48), dp(48)))

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val title = SpannableString("VTX Mouse Pro")
        title.setSpan(ForegroundColorSpan(Vtx.MINT), 10, 13, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        titles.addView(tv(title, 20f, Vtx.TEXT, true))
        subtitle = tv("Gaming Controller", 12f, Vtx.MUTED)
        titles.addView(subtitle, lp(WRAP, WRAP, top = dp(2)))
        row.addView(titles, lp(0, WRAP, 1f, left = dp(12)))

        row.addView(statusPill(), lp(WRAP, WRAP))
        return row
    }

    private fun statusPill(): View {
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = glass(24, 1f)
        pill.setPadding(dp(16), dp(8), dp(16), dp(8))

        statusDot = View(this)
        pill.addView(statusDot, lp(dp(10), dp(10)))
        statusText = tv("Belum terhubung", 13f, Vtx.TEXT, true)
        pill.addView(statusText, lp(WRAP, WRAP, left = dp(8), right = dp(12)))
        pill.addView(vLine(70), lp(dp(1), dp(22)))

        mouseIcon = icon(R.drawable.ic_mouse, Vtx.MUTED, 22)
        keyIcon = icon(R.drawable.ic_keyboard, Vtx.MUTED, 22)
        shizukuIcon = icon(R.drawable.ic_hexagon, Vtx.MUTED, 22)
        pill.addView(mouseIcon, lp(dp(22), dp(22), left = dp(12)))
        pill.addView(keyIcon, lp(dp(22), dp(22), left = dp(12)))
        pill.addView(shizukuIcon, lp(dp(22), dp(22), left = dp(12)))
        return pill
    }

    private fun updateStatus() {
        mouseIcon.setColorFilter(if (hasMouse()) Vtx.MINT else Vtx.MUTED)
        keyIcon.setColorFilter(if (hasKeyboard()) Vtx.MINT else Vtx.MUTED)
        val st = Link.state(this)
        val connected = st == Link.State.CONNECTED
        shizukuIcon.setColorFilter(
            if (connected) Vtx.MINT else if (shizukuInstalled()) Vtx.AMBER else Vtx.MUTED
        )
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(if (connected) Vtx.MINT else Vtx.AMBER)
        statusDot.background = dot
        statusText.text = when (st) {
            Link.State.CONNECTED -> "Terhubung"
            Link.State.CONNECTING -> "Menyambung..."
            Link.State.NEED_PERMISSION -> "Menunggu izin"
            Link.State.NOT_RUNNING -> "Shizuku belum jalan"
            Link.State.NOT_INSTALLED -> "Shizuku belum ada"
            Link.State.ERROR -> "Gagal menyambung"
        }
    }

    private fun hasMouse(): Boolean = inputDevices().any {
        (it.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
    }

    private fun hasKeyboard(): Boolean = inputDevices().any {
        (it.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
            it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && !it.isVirtual
    }

    private fun inputDevices(): List<InputDevice> =
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }

    // ------------------------------------------------------------------ navigasi bawah
    private fun bottomNav(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(14), dp(4), dp(14), dp(10))
        val names = listOf("Game", "Optimisasi Performa", "Pengaturan")
        val icons = listOf(R.drawable.ic_game, R.drawable.ic_speed, R.drawable.ic_settings)
        val tabs = mutableListOf<LinearLayout>()
        for (i in names.indices) {
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.gravity = Gravity.CENTER
            t.setOnClickListener {
                leaveSub()
                select(i)
            }
            t.addView(icon(icons[i], Vtx.MUTED, 24))
            t.addView(tv(names[i], 12f, Vtx.MUTED, true), lp(WRAP, WRAP, top = dp(3)))
            row.addView(t, lp(0, dp(60), 1f, left = if (i > 0) dp(8) else 0))
            tabs.add(t)
        }
        navTabs = tabs
        return row
    }

    private fun select(i: Int) {
        tab = i
        for (j in navTabs.indices) {
            val t = navTabs[j]
            val on = j == i
            t.background = if (on) glass(22, 1.3f, glow = true) else glass(22, 0.55f)
            (t.getChildAt(0) as ImageView).setColorFilter(if (on) Vtx.MINT else Vtx.MUTED)
            (t.getChildAt(1) as TextView).setTextColor(if (on) Vtx.MINT else Vtx.MUTED)
        }
        subtitle.text = if (i == 0) "Gaming Controller" else "Keyboard dan mouse untuk game"
        content.removeAllViews()
        val v = when (i) {
            0 -> gameTab()
            1 -> perfTab()
            else -> settingsTab()
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------------ komponen bersama
    private fun sectionTitle(text: String, iconRes: Int = 0): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        if (iconRes != 0) row.addView(icon(iconRes, Vtx.MINT, 22), lp(dp(22), dp(22), right = dp(10)))
        row.addView(tv(text, 17f, Vtx.TEXT, true))
        val line = View(this)
        line.setBackgroundColor(argb(60, 0x2DF0B8))
        row.addView(line, lp(0, dp(2), 1f, left = dp(12)))
        return row
    }

    private fun iconTile(res: Int): View {
        val t = FrameLayout(this)
        t.background = glass(14, 0.6f)
        t.addView(icon(res, Vtx.MINT, 26), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        return t
    }

    private fun featureRow(res: Int, title: String, sub: String, right: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = glass(20, 0.7f)
        row.setPadding(dp(14), dp(10), dp(14), dp(10))
        row.addView(iconTile(res), lp(dp(48), dp(48)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(tv(title, 16f, Vtx.TEXT, true))
        texts.addView(tv(sub, 12f, Vtx.MUTED), lp(WRAP, WRAP, top = dp(2)))
        row.addView(texts, lp(0, WRAP, 1f, left = dp(14), right = dp(10)))
        row.addView(right, lp(WRAP, WRAP))
        return row
    }

    private fun pill(text: String, ok: Boolean, muted: Boolean = false): View {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.HORIZONTAL
        p.gravity = Gravity.CENTER_VERTICAL
        p.background = glass(18, if (ok) 1.2f else 0.7f, glow = ok)
        p.setPadding(dp(14), dp(7), dp(14), dp(7))
        if (ok) p.addView(icon(R.drawable.ic_check_circle, Vtx.MINT, 16), lp(dp(16), dp(16), right = dp(6)))
        val color = if (muted) Vtx.MUTED else if (ok) Vtx.MINT else Vtx.TEXT
        p.addView(tv(text, 13f, color, true))
        return p
    }

    // ------------------------------------------------------------------ tab Game
    private fun gameTab(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(2), dp(20), dp(8))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(icon(R.drawable.ic_game, Vtx.MINT, 26))
        head.addView(tv("Game Saya", 18f, Vtx.TEXT, true), lp(WRAP, WRAP, left = dp(10), right = dp(12)))
        val line = View(this)
        line.setBackgroundColor(argb(50, 0x2DF0B8))
        head.addView(line, lp(0, dp(1), 1f))
        val add = tv("+ Tambah", 13f, Vtx.MINT, true)
        add.background = glass(16)
        add.setPadding(dp(14), dp(8), dp(14), dp(8))
        add.setOnClickListener { openAddGame() }
        head.addView(add, lp(WRAP, WRAP, left = dp(12)))
        root.addView(head, lp(MATCH, WRAP))

        val games = Prefs.games(this).filter { isInstalled(it) }
        val sel = selectedGame(games)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        if (games.isEmpty()) {
            list.addView(emptyCard(), lp(MATCH, WRAP, top = dp(10)))
        } else {
            for (pkg in games) {
                list.addView(gameCard(pkg, pkg == sel), lp(MATCH, WRAP, top = dp(10)))
            }
        }
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)
        root.addView(scroll, lp(MATCH, 0, 1f))
        root.addView(startButton(sel != null), lp(MATCH, dp(64), top = dp(10)))
        return root
    }

    private fun selectedGame(games: List<String>): String? {
        val s = Prefs.selected(this)
        return if (s in games) s else games.firstOrNull()
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        true
    } catch (e: Exception) {
        false
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        ).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun emptyCard(): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.background = glass(22, 0.8f)
        c.setPadding(dp(20), dp(26), dp(20), dp(26))
        c.addView(tv("Belum ada game", 17f, Vtx.TEXT, true))
        c.addView(tv("Tekan + Tambah untuk memilih game dari aplikasi yang terpasang.", 13f, Vtx.MUTED),
            lp(WRAP, WRAP, top = dp(6)))
        return c
    }

    private fun gameCard(pkg: String, selected: Boolean): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = glass(22, if (selected) 1.3f else 0.9f, glow = selected)
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        card.setOnClickListener {
            Prefs.setSelected(this, pkg)
            select(0)
        }

        val iconView = ImageView(this)
        iconView.setImageDrawable(try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null })
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        card.addView(iconView, lp(dp(54), dp(54)))
        card.addView(vLine(), lp(dp(1), dp(46), left = dp(14), right = dp(14)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(tv(appLabel(pkg), 18f, Vtx.TEXT, true))
        val st = LinearLayout(this)
        st.orientation = LinearLayout.HORIZONTAL
        st.gravity = Gravity.CENTER_VERTICAL
        st.addView(icon(R.drawable.ic_check_circle, Vtx.MINT, 16), lp(dp(16), dp(16), right = dp(6)))
        st.addView(tv("Siap dimainkan", 13f, Vtx.MUTED))
        texts.addView(st, lp(WRAP, WRAP, top = dp(4)))
        card.addView(texts, lp(0, WRAP, 1f))
        card.addView(icon(R.drawable.ic_chevron_right, Vtx.MINT, 24), lp(dp(24), dp(24)))
        return card
    }

    private fun startButton(enabled: Boolean): View {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER
        b.background = glass(30, 1f, glow = true, filled = true)
        b.alpha = if (enabled) 1f else 0.5f
        b.setOnClickListener { startSelected() }
        b.addView(icon(R.drawable.ic_play, Color.WHITE, 26), lp(dp(26), dp(26)))
        b.addView(tv("Mulai Game", 20f, Color.WHITE, true), lp(WRAP, WRAP, left = dp(12)))
        return b
    }

    private fun startSelected() {
        val pkg = selectedGame(Prefs.games(this).filter { isInstalled(it) })
        if (pkg == null) {
            toast("Tambah game dulu")
            return
        }
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            toast("Game tidak bisa dibuka")
            return
        }
        startActivity(i)
    }

    // ------------------------------------------------------------------ halaman Tambah Game
    private fun openAddGame() {
        returnTab = 0
        enterSub(addGamePage())
    }

    private fun openTouchTest() {
        returnTab = 2
        enterSub(touchTestPage())
    }

    private fun enterSub(page: View) {
        showingSub = true
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCb)
        content.removeAllViews()
        content.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun leaveSub() {
        if (!showingSub) return
        showingSub = false
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCb)
    }

    private fun closeSub() {
        leaveSub()
        select(returnTab)
    }

    private fun addGamePage(): View {
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(20), dp(2), dp(20), dp(8))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = tv("\u2039", 28f, Vtx.MINT, true)
        back.gravity = Gravity.CENTER
        back.background = glass(22)
        back.setOnClickListener { closeSub() }
        top.addView(back, lp(dp(44), dp(44)))
        top.addView(tv("Tambah Game", 18f, Vtx.TEXT, true), lp(WRAP, WRAP, left = dp(12)))
        page.addView(top, lp(MATCH, WRAP))

        val search = EditText(this)
        search.hint = "Cari aplikasi"
        search.setHintTextColor(Vtx.MUTED)
        search.setTextColor(Vtx.TEXT)
        search.textSize = 15f
        search.background = glass(22, 0.8f)
        search.setPadding(dp(18), dp(10), dp(18), dp(10))
        search.setSingleLine()
        search.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        page.addView(search, lp(MATCH, dp(46), top = dp(10)))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(tv("Memuat aplikasi...", 14f, Vtx.MUTED), lp(WRAP, WRAP, top = dp(14), left = dp(8)))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(box)
        page.addView(scroll, lp(MATCH, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderApps(box, s?.toString().orEmpty())
            }
        })
        loadApps { renderApps(box, search.text.toString()) }
        return page
    }

    private fun loadApps(done: () -> Unit) {
        if (allApps.isNotEmpty()) {
            done()
            return
        }
        Thread {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val pm = packageManager
            val list = pm.queryIntentActivities(q, PackageManager.ResolveInfoFlags.of(0))
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                allApps = list
                if (showingSub) done()
            }
        }.start()
    }

    private fun renderApps(box: LinearLayout, filter: String) {
        box.removeAllViews()
        val chosen = Prefs.games(this).toSet()
        val f = filter.trim().lowercase()
        val shown = allApps.filter {
            f.isEmpty() || it.label.lowercase().contains(f) || it.pkg.lowercase().contains(f)
        }
        if (shown.isEmpty()) {
            box.addView(tv("Tidak ada hasil", 14f, Vtx.MUTED), lp(WRAP, WRAP, top = dp(14), left = dp(8)))
            return
        }
        for (app in shown) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = glass(18, 0.7f)
            row.setPadding(dp(14), dp(8), dp(14), dp(8))
            val ic = ImageView(this)
            ic.setImageDrawable(app.icon)
            row.addView(ic, lp(dp(40), dp(40)))
            row.addView(tv(app.label, 15f, Vtx.TEXT, true), lp(0, WRAP, 1f, left = dp(12), right = dp(8)))
            val toggle = GlassToggle(this)
            toggle.setInstant(app.pkg in chosen)
            toggle.onToggle = { on ->
                val cur = Prefs.games(this).toMutableList()
                if (on) {
                    if (app.pkg !in cur) cur.add(app.pkg)
                } else {
                    cur.remove(app.pkg)
                }
                Prefs.saveGames(this, cur)
            }
            row.addView(toggle, lp(dp(54), dp(32)))
            box.addView(row, lp(MATCH, WRAP, top = dp(8)))
        }
    }

    // ------------------------------------------------------------------ halaman Tes Sentuhan
    private fun touchTestPage(): View {
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(20), dp(2), dp(20), dp(8))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val back = tv("\u2039", 28f, Vtx.MINT, true)
        back.gravity = Gravity.CENTER
        back.background = glass(22)
        back.setOnClickListener { closeSub() }
        top.addView(back, lp(dp(44), dp(44)))
        top.addView(tv("Tes Sentuhan", 18f, Vtx.TEXT, true), lp(WRAP, WRAP, left = dp(12)))
        page.addView(top, lp(MATCH, WRAP))

        val body = LinearLayout(this)
        body.orientation = LinearLayout.HORIZONTAL
        body.gravity = Gravity.CENTER_VERTICAL
        val target = TouchTargetView(this)
        body.addView(target, lp(dp(170), dp(170), left = dp(10)))

        val side = LinearLayout(this)
        side.orientation = LinearLayout.VERTICAL
        side.setPadding(dp(24), 0, dp(10), 0)
        val result = tv("Tekan Mulai tes. Sistem akan mengetuk lingkaran ini lewat Shizuku.", 15f, Vtx.TEXT)
        side.addView(result, lp(MATCH, WRAP))
        val hint = tv("Kamu juga bisa mengetuk lingkarannya sendiri untuk melihat reaksinya.", 12f, Vtx.MUTED)
        side.addView(hint, lp(MATCH, WRAP, top = dp(8)))
        val start = LinearLayout(this)
        start.orientation = LinearLayout.HORIZONTAL
        start.gravity = Gravity.CENTER
        start.background = glass(26, 1f, glow = true, filled = true)
        start.setOnClickListener { runTouchTest(target, result) }
        start.addView(icon(R.drawable.ic_play, Color.WHITE, 22), lp(dp(22), dp(22)))
        start.addView(tv("Mulai tes", 17f, Color.WHITE, true), lp(WRAP, WRAP, left = dp(10)))
        side.addView(start, lp(dp(220), dp(52), top = dp(16)))
        body.addView(side, lp(0, WRAP, 1f))
        page.addView(body, lp(MATCH, 0, 1f))
        return page
    }

    private fun runTouchTest(target: TouchTargetView, result: TextView) {
        if (!Injector.ready) {
            result.setTextColor(Vtx.RED)
            result.text = "Belum terhubung ke Shizuku."
            return
        }
        target.reset()
        result.setTextColor(Vtx.TEXT)
        result.text = "Mengirim ketukan..."
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val cx = loc[0] + target.width / 2f
        val cy = loc[1] + target.height / 2f
        val h = Handler(Looper.getMainLooper())
        Injector.touch(0, 0, cx, cy)
        h.postDelayed({ Injector.touch(2, 0, 0f, 0f) }, 80)
        h.postDelayed({
            if (target.downCount > 0) {
                result.setTextColor(Vtx.MINT)
                result.text = "Berhasil: sentuhan masuk ke layar. Injeksi bekerja."
            } else {
                Injector.releaseAll()
                result.setTextColor(Vtx.RED)
                result.text = "Gagal: ketukan tidak diterima. Coba sambung ulang Shizuku."
            }
        }, 1200)
    }

    // ------------------------------------------------------------------ tab Optimisasi Performa
    private fun perfTab(): View {
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(20), dp(2), dp(20), dp(8))

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.background = glass(26, 0.9f)
        left.setPadding(dp(16), dp(14), dp(16), dp(12))
        left.addView(sectionTitle("Optimisasi Performa"), lp(MATCH, WRAP))
        val items = listOf(
            Triple(R.drawable.ic_bolt, "Mode Performa", "Prioritaskan performa sistem untuk game"),
            Triple(R.drawable.ic_chip, "Optimisasi CPU", "Kurangi beban CPU saat game berjalan"),
            Triple(R.drawable.ic_ram, "Optimisasi RAM", "Kelola memori agar lebih stabil"),
            Triple(R.drawable.ic_battery, "Hemat Daya", "Kurangi konsumsi daya saat bermain")
        )
        for (item in items) {
            left.addView(featureRow(item.first, item.second, item.third, pill("Segera", false, muted = true)),
                lp(MATCH, WRAP, top = dp(8)))
        }
        row.addView(left, lp(0, WRAP, 1.5f))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.background = glass(26, 0.9f)
        right.setPadding(dp(16), dp(14), dp(16), dp(12))
        right.addView(sectionTitle("Info Perangkat", R.drawable.ic_phone), lp(MATCH, WRAP))
        val info = LinearLayout(this)
        info.orientation = LinearLayout.HORIZONTAL
        info.gravity = Gravity.CENTER_VERTICAL
        info.addView(icon(R.drawable.ic_phone, argb(210, 0x2DF0B8), 64), lp(dp(64), dp(64), right = dp(16)))
        val kv = LinearLayout(this)
        kv.orientation = LinearLayout.VERTICAL
        val pairs = listOf(
            "Model" to DeviceInfo.model(),
            "Android" to DeviceInfo.android(),
            "Arsitektur" to DeviceInfo.arch()
        )
        for (p in pairs) {
            kv.addView(tv(p.first, 12f, Vtx.MUTED), lp(WRAP, WRAP, top = dp(6)))
            kv.addView(tv(p.second, 16f, Vtx.TEXT, true))
        }
        info.addView(kv, lp(0, WRAP, 1f))
        right.addView(info, lp(MATCH, WRAP, top = dp(12)))
        right.addView(statRow(R.drawable.ic_ram, "RAM", DeviceInfo.ram(this)), lp(MATCH, WRAP, top = dp(12)))
        right.addView(statRow(R.drawable.ic_storage, "Penyimpanan", DeviceInfo.storage()), lp(MATCH, WRAP, top = dp(8)))
        row.addView(right, lp(0, WRAP, 1f, left = dp(14)))

        scroll.addView(row)
        return scroll
    }

    private fun statRow(res: Int, label: String, value: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = glass(16, 0.6f)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        r.addView(icon(res, Vtx.MINT, 24), lp(dp(24), dp(24)))
        r.addView(tv(label, 14f, Vtx.MUTED), lp(0, WRAP, 1f, left = dp(12)))
        r.addView(tv(value, 15f, Vtx.TEXT, true))
        return r
    }

    // ------------------------------------------------------------------ tab Pengaturan
    private fun settingsTab(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(20), dp(2), dp(20), dp(8))

        val side = LinearLayout(this)
        side.orientation = LinearLayout.VERTICAL
        side.background = glass(24, 0.8f)
        side.setPadding(dp(10), dp(10), dp(10), dp(10))
        side.addView(sideItem("Umum", R.drawable.ic_settings, settingsPage == 0, 0), lp(MATCH, dp(54)))
        side.addView(sideItem("Tentang", R.drawable.ic_info, settingsPage == 1, 1), lp(MATCH, dp(54), top = dp(6)))
        row.addView(side, lp(dp(150), WRAP))

        val body = ScrollView(this)
        body.isVerticalScrollBarEnabled = false
        body.addView(if (settingsPage == 0) generalPage() else aboutPage())
        row.addView(body, lp(0, MATCH, 1f, left = dp(14)))
        return row
    }

    private fun sideItem(name: String, res: Int, on: Boolean, page: Int): View {
        val itemView = LinearLayout(this)
        itemView.orientation = LinearLayout.HORIZONTAL
        itemView.gravity = Gravity.CENTER_VERTICAL
        itemView.background = if (on) glass(18, 1.3f, glow = true) else glass(18, 0.3f)
        itemView.setPadding(dp(12), 0, dp(12), 0)
        itemView.addView(icon(res, if (on) Vtx.MINT else Vtx.TEXT, 22), lp(dp(22), dp(22)))
        itemView.addView(tv(name, 15f, if (on) Vtx.MINT else Vtx.TEXT, true), lp(WRAP, WRAP, left = dp(10)))
        itemView.setOnClickListener {
            settingsPage = page
            select(2)
        }
        return itemView
    }

    private fun generalPage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val headCard = LinearLayout(this)
        headCard.orientation = LinearLayout.HORIZONTAL
        headCard.gravity = Gravity.CENTER_VERTICAL
        headCard.background = glass(22, 0.9f)
        headCard.setPadding(dp(14), dp(12), dp(14), dp(12))
        headCard.addView(iconTile(R.drawable.ic_shield_check), lp(dp(52), dp(52)))
        val ht = LinearLayout(this)
        ht.orientation = LinearLayout.VERTICAL
        ht.addView(tv("Koneksi & Izin", 17f, Vtx.TEXT, true))
        ht.addView(tv("Kelola izin dan koneksi untuk menggunakan fitur VTX Mouse Pro", 12f, Vtx.MUTED),
            lp(WRAP, WRAP, top = dp(2)))
        headCard.addView(ht, lp(0, WRAP, 1f, left = dp(14)))
        col.addView(headCard, lp(MATCH, WRAP))

        val ls = Link.state(this)
        val shz = when (ls) {
            Link.State.CONNECTED -> Pair("Aktif", true)
            Link.State.CONNECTING -> Pair("Menyambung...", false)
            Link.State.NEED_PERMISSION -> Pair("Izinkan", false)
            Link.State.NOT_RUNNING -> Pair("Belum jalan", false)
            Link.State.NOT_INSTALLED -> Pair("Belum terpasang", false)
            Link.State.ERROR -> Pair("Coba lagi", false)
        }
        val shzSub = when (ls) {
            Link.State.NEED_PERMISSION -> "Tekan untuk memberi izin. Atau Shizuku > Manajemen aplikasi > aktifkan VTX Mouse Pro."
            Link.State.NOT_RUNNING -> "Jalankan Shizuku dulu (debug nirkabel atau root), lalu kembali ke sini."
            Link.State.ERROR -> "Gagal: " + Injector.error.take(80)
            else -> "Aktifkan Shizuku untuk mengakses fitur lanjutan."
        }
        val shzRow = featureRow(R.drawable.ic_hexagon, "Shizuku", shzSub, pill(shz.first, shz.second))
        shzRow.setOnClickListener {
            when (ls) {
                Link.State.NEED_PERMISSION -> ShizukuBridge.requestPermission()
                Link.State.ERROR -> Link.connectNow(this)
                else -> openShizuku()
            }
        }
        col.addView(shzRow, lp(MATCH, WRAP, top = dp(8)))

        val ready = Injector.ready
        val testRow = featureRow(R.drawable.ic_play, "Tes Sentuhan",
            "Ketuk layar lewat Shizuku untuk memastikan injeksi bekerja.",
            pill(if (ready) "Tes" else "Butuh koneksi", false, muted = !ready))
        testRow.setOnClickListener {
            if (Injector.ready) openTouchTest() else toast("Sambungkan Shizuku dulu")
        }
        col.addView(testRow, lp(MATCH, WRAP, top = dp(8)))

        col.addView(featureRow(R.drawable.ic_terminal, "Pairing ADB", "Hubungkan perangkat melalui ADB (opsional).",
            pill("Segera", false, muted = true)), lp(MATCH, WRAP, top = dp(8)))

        val overlayOk = Settings.canDrawOverlays(this)
        val overlayRow = featureRow(R.drawable.ic_layers, "Overlay", "Izinkan aplikasi menampilkan overlay di atas game.",
            pill(if (overlayOk) "Diizinkan" else "Izinkan", overlayOk))
        overlayRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        col.addView(overlayRow, lp(MATCH, WRAP, top = dp(8)))

        val usageOk = usageAccessOk()
        val usageRow = featureRow(R.drawable.ic_eye, "Akses Penggunaan",
            "Untuk logo muncul otomatis saat game dibuka.", pill(if (usageOk) "Aktif" else "Izinkan", usageOk))
        usageRow.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                toast("Tidak bisa membuka pengaturan")
            }
        }
        col.addView(usageRow, lp(MATCH, WRAP, top = dp(8)))

        val toggle = GlassToggle(this)
        toggle.setInstant(notificationsOn())
        toggle.onToggle = { want ->
            if (want && checkSelfPermission(NOTIF_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(NOTIF_PERMISSION), 10)
            } else {
                openNotificationSettings()
            }
        }
        val notifRow = featureRow(R.drawable.ic_bell, "Notifikasi", "Aktifkan notifikasi untuk pembaruan dan info penting.", toggle)
        notifRow.setOnClickListener { openNotificationSettings() }
        col.addView(notifRow, lp(MATCH, WRAP, top = dp(8)))
        return col
    }

    private fun aboutPage(): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = glass(26, 0.9f)
        card.setPadding(dp(18), dp(16), dp(18), dp(16))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val logoTile = FrameLayout(this)
        logoTile.background = glass(18, 1.2f, glow = true)
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_vtx_logo)
        logoTile.addView(logo, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        top.addView(logoTile, lp(dp(64), dp(64)))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.addView(tv("VTX Mouse Pro", 20f, Vtx.TEXT, true))
        t.addView(tv("Keyboard dan mouse untuk game", 13f, Vtx.MUTED), lp(WRAP, WRAP, top = dp(3)))
        top.addView(t, lp(0, WRAP, 1f, left = dp(14)))
        card.addView(top, lp(MATCH, WRAP))

        card.addView(kvRow("Versi", versionName()), lp(MATCH, WRAP, top = dp(14)))
        card.addView(kvRow("Package", packageName), lp(MATCH, WRAP, top = dp(8)))
        card.addView(kvRow("Android didukung", "15 - 16"), lp(MATCH, WRAP, top = dp(8)))
        card.addView(kvRow("Arsitektur", "arm64-v8a (64-bit)"), lp(MATCH, WRAP, top = dp(8)))
        card.addView(kvRow("Perangkat", DeviceInfo.model()), lp(MATCH, WRAP, top = dp(8)))
        return card
    }

    private fun kvRow(k: String, v: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = glass(14, 0.5f)
        r.setPadding(dp(14), dp(10), dp(14), dp(10))
        r.addView(tv(k, 14f, Vtx.MUTED), lp(0, WRAP, 1f))
        r.addView(tv(v, 14f, Vtx.TEXT, true))
        return r
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "-"
    } catch (e: Exception) {
        "-"
    }

    // ------------------------------------------------------------------ status izin
    private fun shizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PKG, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (e: Exception) {
        false
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        try {
            if (i != null) startActivity(i)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        } catch (e: Exception) {
            toast("Tidak bisa membuka Shizuku")
        }
    }

    private fun usageAccessOk(): Boolean = try {
        val ao = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    private fun notificationsOn(): Boolean {
        val granted = checkSelfPermission(NOTIF_PERMISSION) == PackageManager.PERMISSION_GRANTED
        val nm = getSystemService(NotificationManager::class.java)
        return granted && nm.areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Tidak bisa membuka pengaturan")
        }
    }

    private companion object {
        const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        const val NOTIF_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
