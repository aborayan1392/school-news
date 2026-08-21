package com.aborayan.matchclock

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : Activity() {

    private val states = mutableListOf<MatchState>()
    private val prefs by lazy { getSharedPreferences("matchclock-state", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val timeRefs = mutableMapOf<Long, LiveRefs>()

    private lateinit var root: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var empty: TextView
    private lateinit var activeChip: TextView
    private var dark = false
    private var showArchived = false

    private var bigDialog: Dialog? = null
    private var bigStateId: Long? = null
    private var bigTime: TextView? = null
    private var bigScore: TextView? = null
    private var bigSub: TextView? = null

    private data class LiveRefs(
        val time: TextView? = null,
        val lost: TextView? = null,
        val status: TextView? = null,
        val shot: TextView? = null
    )

    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.LIGHT_BG
        window.navigationBarColor = Ui.LIGHT_BG
        loadState()
        buildScreen()
        renderAll()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        bigDialog?.dismiss()
        saveState()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        renderAll()
    }

    private fun ink() = if (dark) Ui.DARK_INK else Ui.LIGHT_INK
    private fun muted() = if (dark) Ui.DARK_MUTED else Ui.LIGHT_MUTED
    private fun surface() = if (dark) Ui.DARK_SURFACE else Ui.LIGHT_SURFACE
    private fun pageBg() = if (dark) Ui.DARK_BG else Ui.LIGHT_BG
    private fun soft() = if (dark) 0xFF1B2A22.toInt() else 0xFFF0F2ED.toInt()
    private fun line() = if (dark) 0xFF304238.toInt() else 0xFFE0E5DF.toInt()

    private fun buildScreen() {
        root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pageBg())
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 88))
        }
        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val header = Ui.horizontal(this, 6).apply { setPadding(2, 2, 2, Ui.dp(this@MainActivity, 10)) }
        val brand = Ui.label(this, "◷  ساعات التوقيت", 18f, ink(), true)
        header.addView(Ui.weight(brand))
        activeChip = Ui.label(this, "0 نشطة", 11f, Ui.GREEN, true).apply {
            gravity = Gravity.CENTER
            background = Ui.bg(if (dark) 0xFF163120.toInt() else 0xFFE6F4E9.toInt(), 99, this@MainActivity, Ui.GREEN)
            setPadding(Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 6))
        }
        header.addView(activeChip)
        val theme = Ui.button(this, if (dark) "☀" else "☾", soft(), ink(), 11, 40, line()).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 44), Ui.dp(this@MainActivity, 44))
            setOnClickListener { dark = !dark; prefs.edit().putBoolean("dark", dark).apply(); buildScreen(); setContentView(root); renderAll() }
        }
        header.addView(theme)
        val menu = Ui.button(this, "⋮", soft(), ink(), 11, 40, line()).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 44), Ui.dp(this@MainActivity, 44))
            setOnClickListener { showMenu(this) }
        }
        header.addView(menu)
        page.addView(header)

        val hint = Ui.label(this, "إدارة التوقيت والنتيجة والأحداث من شاشة واحدة", 12f, muted()).apply {
            setPadding(Ui.dp(this@MainActivity, 4), 0, Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 10))
        }
        page.addView(hint)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        empty = Ui.label(this, "لا توجد بطاقات بعد — اضغط + لبدء أول مباراة", 14f, muted(), true).apply {
            gravity = Gravity.CENTER
            setPadding(20, Ui.dp(this@MainActivity, 72), 20, Ui.dp(this@MainActivity, 72))
        }
        body.addView(empty, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        grid = GridLayout(this).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        body.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val fab = TextView(this).apply {
            text = "+"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = Ui.bg(Ui.GREEN, 99, this@MainActivity)
            elevation = Ui.dp(this@MainActivity, 8).toFloat()
            setOnClickListener { showAddDialog() }
        }
        root.addView(fab, FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 22))
        })
        window.statusBarColor = pageBg()
        window.navigationBarColor = pageBg()
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        setContentView(root)
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "إضافة بطاقة")
            menu.add(0, 2, 1, if (showArchived) "إخفاء الأرشيف" else "عرض الأرشيف")
            menu.add(0, 3, 2, "تصدير نسخة احتياطية")
            menu.add(0, 4, 3, "استيراد نسخة احتياطية")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> showAddDialog()
                    2 -> { showArchived = !showArchived; renderAll() }
                    3 -> exportBackup()
                    4 -> importBackup()
                }
                true
            }
            show()
        }
    }

    private fun showAddDialog() {
        val labels = arrayOf("⚽ ساعة مباراة (قدم / يد)", "🥅 ضربات ترجيح", "🏐 أشواط (طائرة / طاولة)", "🏀 كرة السلة")
        AlertDialog.Builder(this).setTitle("إضافة بطاقة جديدة").setItems(labels) { _, which ->
            val type = when (which) {
                1 -> MatchState.TYPE_SHOOTOUT
                2 -> MatchState.TYPE_SETS
                3 -> MatchState.TYPE_BASKETBALL
                else -> MatchState.TYPE_MATCH
            }
            val title = when (type) {
                MatchState.TYPE_SHOOTOUT -> "ضربات ترجيح"
                MatchState.TYPE_SETS -> "مباراة أشواط"
                MatchState.TYPE_BASKETBALL -> "مباراة كرة سلة"
                else -> "المباراة ${states.size + 1}"
            }
            states.add(MatchState(id = System.currentTimeMillis() + states.size, type = type, title = title))
            saveState(); renderAll()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun renderAll() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        timeRefs.clear()
        val visible = states.filter { showArchived || !it.archived }
        val width = resources.configuration.screenWidthDp
        val columns = when { width >= 1050 -> 3; width >= 650 -> 2; else -> 1 }
        grid.columnCount = columns
        visible.forEachIndexed { index, state ->
            val card = when (state.type) {
                MatchState.TYPE_SHOOTOUT -> buildShootoutCard(state, index + 1)
                MatchState.TYPE_SETS -> buildSetsCard(state, index + 1)
                MatchState.TYPE_BASKETBALL -> buildBasketballCard(state, index + 1)
                else -> buildMatchCard(state, index + 1)
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 6))
            }
            grid.addView(card, lp)
        }
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        updateLive()
    }

    private fun baseCard(state: MatchState): LinearLayout = Ui.vertical(this).apply {
        setPadding(Ui.dp(this@MainActivity, 15), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 15), Ui.dp(this@MainActivity, 14))
        background = Ui.bg(surface(), 20, this@MainActivity, if (state.running) Ui.GREEN else line())
        elevation = Ui.dp(this@MainActivity, 2).toFloat()
    }

    private fun cardHeader(card: LinearLayout, state: MatchState, seq: Int) {
        val row = Ui.horizontal(this, 5)
        val chip = Ui.label(this, "#${seq.toString().padStart(2, '0')}", 10f, muted(), true).apply {
            gravity = Gravity.CENTER; background = Ui.bg(soft(), 8, this@MainActivity); setPadding(8, 5, 8, 5)
        }
        row.addView(chip)
        val title = Ui.label(this, state.title, 15f, ink(), true).apply { setOnClickListener { openSettings(state) } }
        row.addView(Ui.weight(title))
        row.addView(smallButton("⚙") { openSettings(state) })
        row.addView(smallButton("×", Ui.RED) { confirmDelete(state) })
        card.addView(row)
    }

    private fun smallButton(text: String, color: Int = ink(), action: () -> Unit): Button =
        Ui.button(this, text, soft(), color, 9, 36, line()).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 38), Ui.dp(this@MainActivity, 38))
            setOnClickListener { action() }
        }

    private fun bigClock(): TextView = Ui.label(this, "00:00", 44f, ink(), true).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE
        setPadding(2, Ui.dp(this@MainActivity, 9), 2, Ui.dp(this@MainActivity, 6))
    }

    private fun buildMatchCard(s: MatchState, seq: Int): View {
        val card = baseCard(s); cardHeader(card, s, seq)
        val status = Ui.label(this, if (s.running) "● جارٍ التوقيت" else "● متوقفة", 11f, if (s.running) Ui.GREEN else muted(), true)
        card.addView(status)
        val time = bigClock(); card.addView(time)
        val lost = Ui.label(this, "الوقت الضائع  00:00", 11f, Ui.AMBER, true).apply { gravity = Gravity.CENTER }
        card.addView(lost)
        card.addView(scoreBoard(s))
        val controls = Ui.horizontal(this)
        val start = Ui.button(this, if (s.running) "⏸ إيقاف" else "▶ ابدأ", if (s.running) Ui.AMBER else Ui.GREEN, Color.WHITE)
        start.setOnClickListener {
            if (s.running) s.freeze() else s.resume()
            saveState(); renderAll()
        }
        controls.addView(Ui.weight(start))
        val reset = Ui.button(this, "↺ تصفير", soft(), ink(), 13, 48, line()).apply { setOnClickListener { confirmResetMatch(s) } }
        controls.addView(Ui.weight(reset))
        card.addView(controls)

        val eventRow = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0) }
        eventRow.addView(Ui.weight(Ui.button(this, "⚽ هدف", soft(), Ui.GREEN, 12, 44, line()).apply { setOnClickListener { eventDialog(s, "goal") } }))
        eventRow.addView(Ui.weight(Ui.button(this, "🟨 إنذار", soft(), Ui.AMBER, 12, 44, line()).apply { setOnClickListener { eventDialog(s, "yellow") } }))
        eventRow.addView(Ui.weight(Ui.button(this, "🟥 طرد", soft(), Ui.RED, 12, 44, line()).apply { setOnClickListener { eventDialog(s, "red") } }))
        card.addView(eventRow)
        addEventList(card, s)
        addFooter(card, s)
        timeRefs[s.id] = LiveRefs(time, lost, status, null)
        return card
    }

    private fun scoreBoard(s: MatchState): View {
        val row = Ui.horizontal(this, 12).apply { setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8)) }
        row.addView(Ui.weight(teamScore(s, true)))
        row.addView(Ui.label(this, ":", 22f, muted(), true))
        row.addView(Ui.weight(teamScore(s, false)))
        return row
    }

    private fun teamScore(s: MatchState, homeTeam: Boolean): View {
        val box = Ui.vertical(this).apply { gravity = Gravity.CENTER }
        box.addView(Ui.label(this, if (homeTeam) s.home else s.away, 11f, muted(), true).apply { gravity = Gravity.CENTER })
        val row = Ui.horizontal(this, 4)
        val minus = smallButton("−") {
            if (homeTeam) s.scoreHome = (s.scoreHome - 1).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - 1).coerceAtLeast(0)
            saveState(); renderAll()
        }
        row.addView(minus)
        val value = Ui.label(this, (if (homeTeam) s.scoreHome else s.scoreAway).toString(), 22f, ink(), true).apply {
            gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; minWidth = Ui.dp(this@MainActivity, 34)
        }
        row.addView(value)
        row.addView(smallButton("+") {
            if (homeTeam) s.scoreHome++ else s.scoreAway++
            saveState(); renderAll()
        })
        box.addView(row)
        return box
    }

    private fun eventDialog(s: MatchState, kind: String) {
        val teams = arrayOf(s.home, s.away)
        AlertDialog.Builder(this).setTitle("اختر الفريق").setItems(teams) { _, which ->
            val team = if (which == 0) "home" else "away"
            val form = Ui.vertical(this).apply { setPadding(30, 8, 30, 0) }
            val player = EditText(this).apply { hint = "اسم اللاعب (اختياري)"; textDirection = View.TEXT_DIRECTION_RTL }
            val number = EditText(this).apply { hint = "رقم اللاعب (اختياري)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
            form.addView(player); form.addView(number)
            AlertDialog.Builder(this).setTitle(eventName(kind)).setView(form)
                .setPositiveButton("تسجيل") { _, _ ->
                    if (s.autoPause && s.running) s.freeze()
                    if (kind == "goal") { if (team == "home") s.scoreHome++ else s.scoreAway++ }
                    s.events.add(MatchEvent(s.currentElapsed(), kind, team, player.text.toString().trim(), number.text.toString().trim()))
                    saveState(); renderAll()
                }.setNegativeButton("إلغاء", null).show()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun eventName(kind: String) = when (kind) { "goal" -> "هدف"; "yellow" -> "إنذار"; "red" -> "طرد"; else -> kind }

    private fun addEventList(card: LinearLayout, s: MatchState) {
        if (s.events.isEmpty()) return
        card.addView(Ui.label(this, "آخر الأحداث", 11f, muted(), true).apply { setPadding(2, 9, 2, 3) })
        s.events.takeLast(5).asReversed().forEach { e ->
            val team = if (e.team == "home") s.home else s.away
            val person = listOf(e.player, if (e.number.isBlank()) "" else "#${e.number}").filter { it.isNotBlank() }.joinToString(" ")
            val text = "${Ui.formatClock(e.timeMs)}  ${eventName(e.kind)} — $team${if (person.isNotBlank()) " — $person" else ""}"
            card.addView(Ui.label(this, text, 10.5f, muted()).apply { background = Ui.bg(soft(), 8, this@MainActivity); setPadding(9, 6, 9, 6) })
        }
    }

    private fun buildShootoutCard(s: MatchState, seq: Int): View {
        val card = baseCard(s); cardHeader(card, s, seq)
        val scoreHome = s.kicks.count { it.team == "home" && it.scored }
        val scoreAway = s.kicks.count { it.team == "away" && it.scored }
        val score = Ui.label(this, "$scoreHome  :  $scoreAway", 38f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, 12, 0, 6) }
        card.addView(score)
        val winner = shootoutWinner(s)
        val statusText = when {
            winner != null -> "🏆 فاز ${if (winner == "home") s.home else s.away}"
            s.kickOrder.isBlank() -> "اختر من يبدأ ضربات الترجيح"
            else -> "الدور: ${if (currentKicker(s) == "home") s.home else s.away}"
        }
        card.addView(Ui.label(this, statusText, 12f, if (winner != null) Ui.GREEN else muted(), true).apply { gravity = Gravity.CENTER })
        val homeTrack = s.kicks.filter { it.team == "home" }.joinToString("  ") { if (it.scored) "●" else "×" }.ifBlank { "○  ○  ○  ○  ○" }
        val awayTrack = s.kicks.filter { it.team == "away" }.joinToString("  ") { if (it.scored) "●" else "×" }.ifBlank { "○  ○  ○  ○  ○" }
        card.addView(Ui.label(this, "${s.home}:  $homeTrack", 12f, Ui.GREEN, true).apply { gravity = Gravity.CENTER })
        card.addView(Ui.label(this, "${s.away}:  $awayTrack", 12f, Ui.BLUE, true).apply { gravity = Gravity.CENTER })
        val actions = Ui.horizontal(this).apply { setPadding(0, 10, 0, 0) }
        if (s.kickOrder.isBlank()) {
            actions.addView(Ui.weight(Ui.button(this, "${s.home} يبدأ", Ui.GREEN, Color.WHITE).apply { setOnClickListener { s.kickOrder = "home"; saveState(); renderAll() } }))
            actions.addView(Ui.weight(Ui.button(this, "${s.away} يبدأ", Ui.BLUE, Color.WHITE).apply { setOnClickListener { s.kickOrder = "away"; saveState(); renderAll() } }))
        } else if (winner == null) {
            actions.addView(Ui.weight(Ui.button(this, "✓ هدف", Ui.GREEN, Color.WHITE).apply { setOnClickListener { recordKick(s, true) } }))
            actions.addView(Ui.weight(Ui.button(this, "× خطأ", Ui.RED, Color.WHITE).apply { setOnClickListener { recordKick(s, false) } }))
        }
        card.addView(actions)
        val tools = Ui.horizontal(this).apply { setPadding(0, 8, 0, 0) }
        tools.addView(Ui.weight(Ui.button(this, "↶ تراجع", soft(), ink(), 12, 44, line()).apply { setOnClickListener { if (s.kicks.isNotEmpty()) s.kicks.removeAt(s.kicks.lastIndex); saveState(); renderAll() } }))
        tools.addView(Ui.weight(Ui.button(this, "↺ تصفير", soft(), Ui.RED, 12, 44, line()).apply { setOnClickListener { confirmSimpleReset(s) } }))
        card.addView(tools)
        addFooter(card, s)
        return card
    }

    private fun currentKicker(s: MatchState): String {
        if (s.kickOrder.isBlank()) return "home"
        val other = if (s.kickOrder == "home") "away" else "home"
        return if (s.kicks.size % 2 == 0) s.kickOrder else other
    }

    private fun recordKick(s: MatchState, scored: Boolean) {
        if (shootoutWinner(s) != null) return
        s.kicks.add(Kick(currentKicker(s), scored))
        saveState(); renderAll()
    }

    private fun shootoutWinner(s: MatchState): String? {
        val ah = s.kicks.count { it.team == "home" && it.scored }
        val aa = s.kicks.count { it.team == "away" && it.scored }
        val th = s.kicks.count { it.team == "home" }
        val ta = s.kicks.count { it.team == "away" }
        if (th < 5 || ta < 5) {
            if (ah > aa + (5 - ta)) return "home"
            if (aa > ah + (5 - th)) return "away"
            return null
        }
        return if (th == ta && ah != aa) if (ah > aa) "home" else "away" else null
    }

    private fun buildSetsCard(s: MatchState, seq: Int): View {
        val card = baseCard(s); cardHeader(card, s, seq)
        val finished = s.setsHome >= s.setsToWin || s.setsAway >= s.setsToWin
        card.addView(Ui.label(this, if (finished) "🏆 ${if (s.setsHome > s.setsAway) s.home else s.away}" else "الأشواط  ${s.setsHome} - ${s.setsAway}", 13f, if (finished) Ui.GREEN else muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, 7, 0, 0) })
        val live = Ui.label(this, "${s.setPointsHome}  :  ${s.setPointsAway}", 40f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, 6, 0, 5) }
        card.addView(live)
        card.addView(Ui.label(this, "${s.home}                                      ${s.away}", 10f, muted(), true).apply { gravity = Gravity.CENTER })
        if (!finished) {
            val row = Ui.horizontal(this)
            row.addView(Ui.weight(Ui.button(this, "+ نقطة ${s.home}", Ui.GREEN, Color.WHITE).apply { setOnClickListener { addSetPoint(s, "home") } }))
            row.addView(Ui.weight(Ui.button(this, "+ نقطة ${s.away}", Ui.BLUE, Color.WHITE).apply { setOnClickListener { addSetPoint(s, "away") } }))
            card.addView(row)
        }
        if (s.setHistory.isNotEmpty()) {
            val hist = s.setHistory.mapIndexed { i, r -> "${i + 1}) ${r.home}-${r.away}" }.joinToString("   ")
            card.addView(Ui.label(this, hist, 11f, muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, 8, 0, 0) })
        }
        val tools = Ui.horizontal(this).apply { setPadding(0, 8, 0, 0) }
        tools.addView(Ui.weight(Ui.button(this, "↶ تراجع", soft(), ink(), 12, 44, line()).apply { setOnClickListener { undoSetPoint(s) } }))
        tools.addView(Ui.weight(Ui.button(this, "↺ تصفير", soft(), Ui.RED, 12, 44, line()).apply { setOnClickListener { confirmSimpleReset(s) } }))
        card.addView(tools)
        addFooter(card, s)
        return card
    }

    private fun addSetPoint(s: MatchState, team: String) {
        s.pointHistory.add(team)
        if (team == "home") s.setPointsHome++ else s.setPointsAway++
        val setNo = s.setsHome + s.setsAway + 1
        val deciding = setNo >= s.setsToWin * 2 - 1
        val target = if (deciding) s.decidingPoints else s.pointsPerSet
        val high = maxOf(s.setPointsHome, s.setPointsAway)
        if (high >= target && abs(s.setPointsHome - s.setPointsAway) >= 2) {
            s.setHistory.add(SetResult(s.setPointsHome, s.setPointsAway))
            if (s.setPointsHome > s.setPointsAway) s.setsHome++ else s.setsAway++
            s.setPointsHome = 0; s.setPointsAway = 0; s.pointHistory.clear()
        }
        saveState(); renderAll()
    }

    private fun undoSetPoint(s: MatchState) {
        if (s.pointHistory.isEmpty()) return
        val last = s.pointHistory.removeAt(s.pointHistory.lastIndex)
        if (last == "home") s.setPointsHome = (s.setPointsHome - 1).coerceAtLeast(0) else s.setPointsAway = (s.setPointsAway - 1).coerceAtLeast(0)
        saveState(); renderAll()
    }

    private fun buildBasketballCard(s: MatchState, seq: Int): View {
        val card = baseCard(s); cardHeader(card, s, seq)
        val top = Ui.horizontal(this)
        top.addView(Ui.weight(Ui.label(this, if (s.quarter <= 4) "الربع ${s.quarter}" else "إضافي ${s.quarter - 4}", 12f, muted(), true).apply { gravity = Gravity.CENTER }))
        val shot = Ui.label(this, "24", 14f, Ui.AMBER, true).apply { gravity = Gravity.CENTER; background = Ui.bg(soft(), 99, this@MainActivity); setPadding(13, 5, 13, 5) }
        top.addView(shot)
        card.addView(top)
        val time = bigClock(); card.addView(time)
        card.addView(scoreBoard(s))
        val controls = Ui.horizontal(this)
        controls.addView(Ui.weight(Ui.button(this, if (s.running) "⏸ إيقاف" else "▶ ابدأ", if (s.running) Ui.AMBER else Ui.GREEN, Color.WHITE).apply {
            setOnClickListener {
                if (s.running) s.freeze() else if (s.basketballRemaining() > 0) s.resume()
                saveState(); renderAll()
            }
        }))
        controls.addView(Ui.weight(Ui.button(this, "↻ 24 ثانية", soft(), Ui.AMBER, 12, 48, line()).apply { setOnClickListener { resetShot(s) } }))
        card.addView(controls)
        val homePts = Ui.horizontal(this).apply { setPadding(0, 8, 0, 0) }
        listOf(1, 2, 3).forEach { pts -> homePts.addView(Ui.weight(Ui.button(this, "+$pts ${s.home}", soft(), Ui.GREEN, 11, 42, line()).apply { setOnClickListener { addBasket(s, "home", pts) } })) }
        card.addView(homePts)
        val awayPts = Ui.horizontal(this).apply { setPadding(0, 6, 0, 0) }
        listOf(1, 2, 3).forEach { pts -> awayPts.addView(Ui.weight(Ui.button(this, "+$pts ${s.away}", soft(), Ui.BLUE, 11, 42, line()).apply { setOnClickListener { addBasket(s, "away", pts) } })) }
        card.addView(awayPts)
        val fouls = Ui.horizontal(this).apply { setPadding(0, 7, 0, 0) }
        fouls.addView(Ui.weight(Ui.button(this, "خطأ ${s.home}: ${s.foulsHome}${if (s.foulsHome >= 5) "  BONUS" else ""}", soft(), if (s.foulsHome >= 5) Ui.RED else ink(), 11, 42, line()).apply { setOnClickListener { s.foulsHome++; saveState(); renderAll() } }))
        fouls.addView(Ui.weight(Ui.button(this, "خطأ ${s.away}: ${s.foulsAway}${if (s.foulsAway >= 5) "  BONUS" else ""}", soft(), if (s.foulsAway >= 5) Ui.RED else ink(), 11, 42, line()).apply { setOnClickListener { s.foulsAway++; saveState(); renderAll() } }))
        card.addView(fouls)
        val tools = Ui.horizontal(this).apply { setPadding(0, 7, 0, 0) }
        tools.addView(Ui.weight(Ui.button(this, "↶ تراجع نقطة", soft(), ink(), 11, 42, line()).apply { setOnClickListener { undoBasket(s) } }))
        tools.addView(Ui.weight(Ui.button(this, "الربع التالي", soft(), ink(), 11, 42, line()).apply { setOnClickListener { nextQuarter(s) } }))
        card.addView(tools)
        addFooter(card, s)
        timeRefs[s.id] = LiveRefs(time, null, null, shot)
        return card
    }

    private fun addBasket(s: MatchState, team: String, points: Int) {
        if (team == "home") s.scoreHome += points else s.scoreAway += points
        s.basketHistory.add("$team:$points")
        resetShot(s, redraw = false)
        saveState(); renderAll()
    }

    private fun undoBasket(s: MatchState) {
        if (s.basketHistory.isEmpty()) return
        val last = s.basketHistory.removeAt(s.basketHistory.lastIndex).split(":")
        val pts = last.getOrNull(1)?.toIntOrNull() ?: return
        if (last.firstOrNull() == "home") s.scoreHome = (s.scoreHome - pts).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - pts).coerceAtLeast(0)
        saveState(); renderAll()
    }

    private fun resetShot(s: MatchState, redraw: Boolean = true) {
        s.shotElapsedMs = 0L
        s.shotStartedAt = if (s.running) System.currentTimeMillis() else 0L
        if (redraw) { saveState(); renderAll() }
    }

    private fun nextQuarter(s: MatchState) {
        if (s.running) s.freeze()
        s.quarter++
        s.elapsedMs = 0L; s.startedAt = 0L; s.foulsHome = 0; s.foulsAway = 0
        s.shotElapsedMs = 0L; s.shotStartedAt = 0L
        saveState(); renderAll()
    }

    private fun addFooter(card: LinearLayout, s: MatchState) {
        val row = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@MainActivity, 10), 0, 0) }
        row.addView(Ui.weight(Ui.button(this, "📄 تقرير", soft(), ink(), 11, 42, line()).apply { setOnClickListener { shareReport(s) } }))
        row.addView(Ui.weight(Ui.button(this, "⛶ تكبير", soft(), ink(), 11, 42, line()).apply { setOnClickListener { showBigScreen(s) } }))
        row.addView(Ui.weight(Ui.button(this, if (s.archived) "↩ استعادة" else "📦 أرشفة", soft(), if (s.archived) Ui.GREEN else muted(), 11, 42, line()).apply {
            setOnClickListener { s.archived = !s.archived; if (s.archived && s.running) s.freeze(); saveState(); renderAll() }
        }))
        card.addView(row)
    }

    private fun updateLive() {
        val now = System.currentTimeMillis()
        var changed = false
        states.forEach { s ->
            val refs = timeRefs[s.id]
            when (s.type) {
                MatchState.TYPE_MATCH -> {
                    refs?.time?.text = Ui.formatClock(s.currentElapsed(now), true)
                    refs?.lost?.text = "الوقت الضائع  ${Ui.formatLost(s.currentLost(now))}"
                    refs?.status?.apply {
                        text = if (s.running) "● جارٍ التوقيت" else "● متوقفة"
                        setTextColor(if (s.running) Ui.GREEN else muted())
                    }
                }
                MatchState.TYPE_BASKETBALL -> {
                    val remaining = s.basketballRemaining(now)
                    refs?.time?.text = Ui.formatClock(remaining)
                    val shotSec = ((s.shotRemaining(now) + 999) / 1000).toInt()
                    refs?.shot?.text = shotSec.toString()
                    refs?.shot?.setTextColor(if (shotSec <= 5) Ui.RED else Ui.AMBER)
                    if (s.running && remaining <= 0L) { s.freeze(now); changed = true }
                }
            }
        }
        if (changed) saveState()
        val active = states.count { it.running && !it.archived }
        if (::activeChip.isInitialized) activeChip.text = "$active نشطة"
        if (active > 0) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateBig(now)
    }

    private fun openSettings(s: MatchState) {
        val form = Ui.vertical(this).apply { setPadding(34, 4, 34, 2) }
        val title = edit("اسم البطاقة", s.title); val home = edit("اسم الفريق الأول", s.home); val away = edit("اسم الفريق الثاني", s.away)
        val referee = edit("اسم الحكم (اختياري)", s.referee); val notes = edit("ملاحظات (اختياري)", s.notes)
        form.addView(title); form.addView(home); form.addView(away); form.addView(referee); form.addView(notes)
        var extra: EditText? = null
        if (s.type == MatchState.TYPE_MATCH) {
            extra = edit("مدة الشوط بالدقائق", s.halfMinutes.toString(), true); form.addView(extra)
        } else if (s.type == MatchState.TYPE_SETS) {
            extra = edit("نقاط الشوط", s.pointsPerSet.toString(), true); form.addView(extra)
        } else if (s.type == MatchState.TYPE_BASKETBALL) {
            extra = edit("دقائق الربع", s.quarterMinutes.toString(), true); form.addView(extra)
        }
        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this).setTitle("إعدادات البطاقة").setView(scroll)
            .setPositiveButton("حفظ") { _, _ ->
                s.title = title.text.toString().trim().ifBlank { s.title }
                s.home = home.text.toString().trim().ifBlank { "الفريق ١" }
                s.away = away.text.toString().trim().ifBlank { "الفريق ٢" }
                s.referee = referee.text.toString().trim(); s.notes = notes.text.toString().trim()
                extra?.text?.toString()?.toIntOrNull()?.let {
                    when (s.type) {
                        MatchState.TYPE_MATCH -> s.halfMinutes = it.coerceIn(1, 200)
                        MatchState.TYPE_SETS -> s.pointsPerSet = it.coerceIn(1, 100)
                        MatchState.TYPE_BASKETBALL -> s.quarterMinutes = it.coerceIn(1, 30)
                    }
                }
                saveState(); renderAll()
            }.setNeutralButton(if (s.type == MatchState.TYPE_MATCH) if (s.autoPause) "إيقاف التوقف التلقائي" else "تفعيل التوقف التلقائي" else "") { _, _ ->
                if (s.type == MatchState.TYPE_MATCH) { s.autoPause = !s.autoPause; saveState(); renderAll() }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun edit(hintText: String, value: String, numeric: Boolean = false): EditText = EditText(this).apply {
        hint = hintText; setText(value); textDirection = View.TEXT_DIRECTION_RTL
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setPadding(10, 8, 10, 8)
    }

    private fun confirmDelete(s: MatchState) {
        AlertDialog.Builder(this).setTitle("حذف البطاقة؟").setMessage("سيتم حذف ${s.title} وكل بياناتها نهائيًا.")
            .setPositiveButton("حذف") { _, _ -> states.remove(s); saveState(); renderAll() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun confirmResetMatch(s: MatchState) {
        AlertDialog.Builder(this).setTitle("تصفير المباراة؟").setMessage("سيتم تصفير الوقت والنتيجة والأحداث.")
            .setPositiveButton("تصفير") { _, _ ->
                s.running = false; s.startedAt = 0; s.elapsedMs = 0; s.lostMs = 0; s.pausedAt = 0
                s.scoreHome = 0; s.scoreAway = 0; s.events.clear(); saveState(); renderAll()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun confirmSimpleReset(s: MatchState) {
        AlertDialog.Builder(this).setTitle("تصفير البطاقة؟").setMessage("سيتم مسح التقدم المسجل في هذه البطاقة.")
            .setPositiveButton("تصفير") { _, _ ->
                when (s.type) {
                    MatchState.TYPE_SHOOTOUT -> { s.kicks.clear(); s.kickOrder = "" }
                    MatchState.TYPE_SETS -> { s.setPointsHome = 0; s.setPointsAway = 0; s.setsHome = 0; s.setsAway = 0; s.setHistory.clear(); s.pointHistory.clear() }
                }
                saveState(); renderAll()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun reportText(s: MatchState): String {
        val header = "${s.title}\n${s.home} ${s.scoreHome} - ${s.scoreAway} ${s.away}"
        return when (s.type) {
            MatchState.TYPE_MATCH -> {
                val ev = s.events.joinToString("\n") { "${Ui.formatClock(it.timeMs)} — ${eventName(it.kind)} — ${if (it.team == "home") s.home else s.away}${if (it.player.isNotBlank()) " — ${it.player}" else ""}" }
                "$header\nمدة اللعب: ${Ui.formatClock(s.currentElapsed())}\nالوقت الضائع: ${Ui.formatLost(s.currentLost())}${if (ev.isNotBlank()) "\n\nالأحداث:\n$ev" else ""}"
            }
            MatchState.TYPE_SHOOTOUT -> "$header\nضربات الترجيح: ${s.kicks.count { it.team == "home" && it.scored }} - ${s.kicks.count { it.team == "away" && it.scored }}"
            MatchState.TYPE_SETS -> "$header\nالأشواط: ${s.setsHome} - ${s.setsAway}\n${s.setHistory.joinToString("، ") { "${it.home}-${it.away}" }}"
            MatchState.TYPE_BASKETBALL -> "$header\nالربع: ${s.quarter}\nأخطاء الفرق: ${s.foulsHome} - ${s.foulsAway}"
            else -> header
        } + if (s.referee.isNotBlank()) "\nالحكم: ${s.referee}" else ""
    }

    private fun shareReport(s: MatchState) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, s.title); putExtra(Intent.EXTRA_TEXT, reportText(s))
        }, "مشاركة تقرير المباراة"))
    }

    private fun showBigScreen(s: MatchState) {
        bigDialog?.dismiss()
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val box = Ui.vertical(this).apply { setBackgroundColor(0xFF05080A.toInt()); gravity = Gravity.CENTER; setPadding(30, 30, 30, 30) }
        val close = Ui.button(this, "× إغلاق", 0xFF20252A.toInt(), Color.WHITE, 12, 46).apply { setOnClickListener { d.dismiss() } }
        box.addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START })
        box.addView(Ui.label(this, "${s.home}          ${s.away}", 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, 25, 0, 5) })
        bigTime = Ui.label(this, "", 72f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        box.addView(bigTime)
        bigScore = Ui.label(this, "", 58f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        box.addView(bigScore)
        bigSub = Ui.label(this, s.title, 16f, 0xFF9AA5A0.toInt(), true).apply { gravity = Gravity.CENTER }
        box.addView(bigSub)
        d.setContentView(box)
        d.setOnDismissListener { bigStateId = null; bigDialog = null; bigTime = null; bigScore = null; bigSub = null }
        bigStateId = s.id; bigDialog = d; d.show(); updateBig(System.currentTimeMillis())
    }

    private fun updateBig(now: Long) {
        val id = bigStateId ?: return
        val s = states.firstOrNull { it.id == id } ?: return
        bigScore?.text = when (s.type) {
            MatchState.TYPE_SHOOTOUT -> "${s.kicks.count { it.team == "home" && it.scored }} : ${s.kicks.count { it.team == "away" && it.scored }}"
            MatchState.TYPE_SETS -> "${s.setPointsHome} : ${s.setPointsAway}"
            else -> "${s.scoreHome} : ${s.scoreAway}"
        }
        bigTime?.text = when (s.type) {
            MatchState.TYPE_MATCH -> Ui.formatClock(s.currentElapsed(now))
            MatchState.TYPE_BASKETBALL -> Ui.formatClock(s.basketballRemaining(now))
            MatchState.TYPE_SETS -> "${s.setsHome} - ${s.setsAway}"
            else -> ""
        }
        bigSub?.text = when (s.type) {
            MatchState.TYPE_BASKETBALL -> "${s.title} — الربع ${s.quarter} — 24s: ${((s.shotRemaining(now) + 999) / 1000)}"
            MatchState.TYPE_SHOOTOUT -> shootoutWinner(s)?.let { "🏆 ${if (it == "home") s.home else s.away}" } ?: s.title
            else -> s.title
        }
    }

    private fun saveState() {
        try {
            val root = JSONObject().apply {
                put("version", 1); put("dark", dark)
                put("items", JSONArray().apply { states.forEach { put(it.toJson()) } })
            }
            prefs.edit().putString("json", root.toString()).putBoolean("dark", dark).apply()
        } catch (_: Exception) { }
    }

    private fun loadState() {
        dark = prefs.getBoolean("dark", false)
        val raw = prefs.getString("json", null) ?: return
        try {
            val arr = JSONObject(raw).optJSONArray("items") ?: return
            states.clear()
            for (i in 0 until arr.length()) states.add(MatchState.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) { states.clear() }
    }

    private fun backupJson(): String = JSONObject().apply {
        put("app", "MatchClock"); put("version", 1); put("exportedAt", System.currentTimeMillis())
        put("items", JSONArray().apply { states.forEach { put(it.toJson()) } })
    }.toString(2)

    private fun exportBackup() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "matchclock-backup.json")
        }, REQ_EXPORT)
    }

    private fun importBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        }, REQ_IMPORT)
    }

    @Deprecated("Deprecated in Android SDK but retained for minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == REQ_EXPORT) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backupJson()) }
                Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } else if (requestCode == REQ_IMPORT) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                val arr = JSONObject(raw).optJSONArray("items") ?: throw IllegalArgumentException("ملف غير صالح")
                val imported = mutableListOf<MatchState>()
                for (i in 0 until arr.length()) imported.add(MatchState.fromJson(arr.getJSONObject(i)))
                states.clear(); states.addAll(imported); saveState(); renderAll()
                Toast.makeText(this, "تم استيراد ${imported.size} بطاقة", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة الملف: ${e.message ?: "خطأ"}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQ_EXPORT = 501
        private const val REQ_IMPORT = 502
    }
}
