package com.aborayan.matchclock

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class RefereeActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("matchclock-state", MODE_PRIVATE) }
    private val states = mutableListOf<MatchState>()
    private val liveTimes = mutableMapOf<Long, TextView>()
    private val liveLost = mutableMapOf<Long, TextView>()
    private val liveShots = mutableMapOf<Long, TextView>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var empty: TextView
    private lateinit var active: TextView
    private var dark = false
    private var showArchived = false

    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        buildShell()
        render()
        handler.post(ticker)
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        save()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
    private fun bg(): Int = if (dark) 0xFF08110D.toInt() else 0xFFF4F5EF.toInt()
    private fun surface(): Int = if (dark) 0xFF132019.toInt() else Color.WHITE
    private fun ink(): Int = if (dark) 0xFFF3F7F4.toInt() else 0xFF17231B.toInt()
    private fun muted(): Int = if (dark) 0xFFA8B8AF.toInt() else 0xFF65746A.toInt()
    private fun soft(): Int = if (dark) 0xFF1C2B23.toInt() else 0xFFF0F2ED.toInt()
    private fun line(): Int = if (dark) 0xFF33443A.toInt() else 0xFFDDE4DD.toInt()
    private val green = 0xFF2F9C4D.toInt()
    private val blue = 0xFF2675C7.toInt()
    private val amber = 0xFFC48325.toInt()
    private val red = 0xFFC44440.toInt()

    private fun shape(color: Int, radius: Int = 14, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun text(value: String, size: Float = 13f, color: Int = ink(), bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(4), dp(3), dp(4), dp(3))
    }

    private fun btn(label: String, fill: Int = soft(), color: Int = ink(), heightDp: Int = 46, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(heightDp)
        minimumHeight = dp(heightDp)
        minimumWidth = 0
        background = shape(fill, 12, if (fill == soft()) line() else null)
        setPadding(dp(7), 0, dp(7), 0)
        setOnClickListener { onClick() }
    }

    private fun row(gap: Int = 7): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = object : android.graphics.drawable.ColorDrawable(Color.TRANSPARENT) {
            override fun getIntrinsicWidth(): Int = dp(gap)
        }
    }

    private fun col(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun weighted(v: View): View = v.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }

    private fun buildShell() {
        root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bg())
        }
        val page = col().apply { setPadding(dp(12), dp(12), dp(12), dp(82)) }
        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val header = row(6)
        header.addView(weighted(text("◷  ساعات التوقيت", 19f, ink(), true)))
        active = text("0 نشطة", 11f, green, true).apply {
            gravity = Gravity.CENTER
            background = shape(if (dark) 0xFF163421.toInt() else 0xFFE5F4E8.toInt(), 99, green)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        header.addView(active)
        header.addView(btn(if (dark) "☀" else "☾", soft(), ink(), 42) {
            dark = !dark
            prefs.edit().putBoolean("dark", dark).apply()
            buildShell(); render()
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) })
        header.addView(btn("⋮", soft(), ink(), 42) { showMenu() }.apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) })
        page.addView(header)
        page.addView(text("مؤقتات ونتائج وأحداث مناسبة للتحكيم من الجوال أو التابلت", 11.5f, muted()).apply { setPadding(dp(4), dp(3), dp(4), dp(8)) })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = col()
        empty = text("لا توجد بطاقات بعد — اضغط + لبدء مباراة", 14f, muted(), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(70), dp(10), dp(70))
        }
        body.addView(empty)
        grid = GridLayout(this).apply { alignmentMode = GridLayout.ALIGN_BOUNDS }
        body.addView(grid)
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val fab = text("+", 31f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = shape(green, 99)
            elevation = dp(8).toFloat()
            setOnClickListener { addCardDialog() }
        }
        root.addView(fab, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(18), dp(18), dp(18), dp(20))
        })

        window.statusBarColor = bg()
        window.navigationBarColor = bg()
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(root)
    }

    private fun showMenu() {
        val options = arrayOf("إضافة بطاقة", if (showArchived) "إخفاء الأرشيف" else "عرض الأرشيف", "تصدير نسخة JSON", "استيراد نسخة JSON")
        AlertDialog.Builder(this).setTitle("القائمة").setItems(options) { _, i ->
            when (i) {
                0 -> addCardDialog()
                1 -> { showArchived = !showArchived; render() }
                2 -> exportBackup()
                3 -> importBackup()
            }
        }.show()
    }

    private fun addCardDialog() {
        val labels = arrayOf("⚽ ساعة مباراة", "🥅 ضربات ترجيح", "🏐 أشواط", "🏀 كرة سلة")
        AlertDialog.Builder(this).setTitle("بطاقة جديدة").setItems(labels) { _, i ->
            val type = when (i) {
                1 -> MatchState.TYPE_SHOOTOUT
                2 -> MatchState.TYPE_SETS
                3 -> MatchState.TYPE_BASKETBALL
                else -> MatchState.TYPE_MATCH
            }
            val title = when (type) {
                MatchState.TYPE_SHOOTOUT -> "ضربات ترجيح"
                MatchState.TYPE_SETS -> "مباراة أشواط"
                MatchState.TYPE_BASKETBALL -> "كرة السلة"
                else -> "المباراة ${states.size + 1}"
            }
            states.add(MatchState(type = type, title = title))
            save(); render()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun render() {
        if (!::grid.isInitialized) return
        grid.removeAllViews(); liveTimes.clear(); liveLost.clear(); liveShots.clear()
        val list = states.filter { showArchived || !it.archived }
        val columns = when {
            resources.configuration.screenWidthDp >= 1050 -> 3
            resources.configuration.screenWidthDp >= 650 -> 2
            else -> 1
        }
        grid.columnCount = columns
        list.forEachIndexed { index, state ->
            val card = when (state.type) {
                MatchState.TYPE_SHOOTOUT -> shootoutCard(state, index + 1)
                MatchState.TYPE_SETS -> setsCard(state, index + 1)
                MatchState.TYPE_BASKETBALL -> basketCard(state, index + 1)
                else -> matchCard(state, index + 1)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            lp.setMargins(dp(5), dp(6), dp(5), dp(6))
            grid.addView(card, lp)
        }
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        updateLive()
    }

    private fun cardBase(s: MatchState, n: Int): LinearLayout = col().apply {
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = shape(surface(), 19, if (s.running) green else line())
        elevation = dp(2).toFloat()
        val h = row(5)
        h.addView(text("#${n.toString().padStart(2, '0')}", 10f, muted(), true).apply { background = shape(soft(), 8) })
        h.addView(weighted(text(s.title, 15f, ink(), true).apply { setOnClickListener { settings(s) } }))
        h.addView(btn("⚙", soft(), ink(), 38) { settings(s) }.apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) })
        h.addView(btn("×", soft(), red, 38) { deleteConfirm(s) }.apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) })
        addView(h)
    }

    private fun matchCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val status = text(if (s.running) "● جارٍ التوقيت" else "● متوقفة", 11f, if (s.running) green else muted(), true)
        card.addView(status)
        val time = text("00:00.0", 43f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, dp(8), 0, 0) }
        card.addView(time); liveTimes[s.id] = time
        val lost = text("الوقت الضائع 00:00", 11f, amber, true).apply { gravity = Gravity.CENTER }
        card.addView(lost); liveLost[s.id] = lost
        card.addView(scoreControls(s))
        val controls = row()
        controls.addView(weighted(btn(if (s.running) "⏸ إيقاف" else "▶ ابدأ", if (s.running) amber else green, Color.WHITE) {
            if (s.running) s.freeze() else s.resume(); save(); render()
        }))
        controls.addView(weighted(btn("↺ تصفير", soft(), ink()) { resetMatch(s) }))
        card.addView(controls)

        val events = row().apply { setPadding(0, dp(8), 0, 0) }
        events.addView(weighted(btn("⚽ هدف", soft(), green, 43) { addEvent(s, "goal") }))
        events.addView(weighted(btn("🟨 إنذار", soft(), amber, 43) { addEvent(s, "yellow") }))
        events.addView(weighted(btn("🟥 طرد", soft(), red, 43) { addEvent(s, "red") }))
        card.addView(events)
        if (s.sport == "handball") card.addView(btn("⏱ إيقاف دقيقتين", soft(), amber, 42) { addEvent(s, "suspension") })

        s.events.takeLast(4).asReversed().forEach { e ->
            val teamName = if (e.team == "home") s.home else s.away
            val kind = when (e.kind) { "goal" -> "هدف"; "yellow" -> "إنذار"; "red" -> "طرد"; "suspension" -> "إيقاف دقيقتين"; else -> e.kind }
            card.addView(text("${clock(e.timeMs)} — $kind — $teamName${if (e.player.isNotBlank()) " — ${e.player}" else ""}", 10.5f, muted()).apply {
                background = shape(soft(), 8); setPadding(dp(8), dp(6), dp(8), dp(6))
            })
        }
        footer(card, s)
        return card
    }

    private fun scoreControls(s: MatchState): View {
        val r = row(10).apply { setPadding(0, dp(7), 0, dp(8)) }
        r.addView(weighted(teamBox(s, true)))
        r.addView(text(":", 22f, muted(), true))
        r.addView(weighted(teamBox(s, false)))
        return r
    }

    private fun teamBox(s: MatchState, homeTeam: Boolean): View {
        val box = col().apply { gravity = Gravity.CENTER }
        box.addView(text(if (homeTeam) s.home else s.away, 11f, muted(), true).apply { gravity = Gravity.CENTER })
        val r = row(4)
        r.addView(btn("−", soft(), ink(), 38) {
            if (homeTeam) s.scoreHome = (s.scoreHome - 1).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - 1).coerceAtLeast(0)
            save(); render()
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) })
        r.addView(text((if (homeTeam) s.scoreHome else s.scoreAway).toString(), 23f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; minWidth = dp(36) })
        r.addView(btn("+", soft(), ink(), 38) {
            if (homeTeam) s.scoreHome++ else s.scoreAway++
            save(); render()
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) })
        box.addView(r)
        return box
    }

    private fun addEvent(s: MatchState, kind: String) {
        AlertDialog.Builder(this).setTitle("اختر الفريق").setItems(arrayOf(s.home, s.away)) { _, which ->
            val team = if (which == 0) "home" else "away"
            val input = EditText(this).apply { hint = "اسم اللاعب أو رقمه (اختياري)"; textDirection = View.TEXT_DIRECTION_RTL }
            AlertDialog.Builder(this).setTitle("تفاصيل الحدث").setView(input)
                .setPositiveButton("تسجيل") { _, _ ->
                    if (s.autoPause && s.running) s.freeze()
                    if (kind == "goal") { if (team == "home") s.scoreHome++ else s.scoreAway++ }
                    s.events.add(MatchEvent(s.currentElapsed(), kind, team, input.text.toString().trim()))
                    save(); render()
                }.setNegativeButton("إلغاء", null).show()
        }.show()
    }

    private fun shootoutCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val hs = s.kicks.count { it.team == "home" && it.scored }
        val ascore = s.kicks.count { it.team == "away" && it.scored }
        card.addView(text("$hs  :  $ascore", 40f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, dp(10), 0, dp(4)) })
        val winner = shootoutWinner(s)
        card.addView(text(when {
            winner != null -> "🏆 ${if (winner == "home") s.home else s.away}"
            s.kickOrder.isBlank() -> "اختر الفريق الذي يبدأ"
            else -> "الدور: ${if (currentKicker(s) == "home") s.home else s.away}"
        }, 12f, if (winner != null) green else muted(), true).apply { gravity = Gravity.CENTER })
        card.addView(text("${s.home}: ${kickTrack(s, "home")}", 12f, green, true).apply { gravity = Gravity.CENTER })
        card.addView(text("${s.away}: ${kickTrack(s, "away")}", 12f, blue, true).apply { gravity = Gravity.CENTER })
        val r = row().apply { setPadding(0, dp(9), 0, 0) }
        if (s.kickOrder.isBlank()) {
            r.addView(weighted(btn("${s.home} يبدأ", green, Color.WHITE) { s.kickOrder = "home"; save(); render() }))
            r.addView(weighted(btn("${s.away} يبدأ", blue, Color.WHITE) { s.kickOrder = "away"; save(); render() }))
        } else if (winner == null) {
            r.addView(weighted(btn("✓ هدف", green, Color.WHITE) { s.kicks.add(Kick(currentKicker(s), true)); save(); render() }))
            r.addView(weighted(btn("× خطأ", red, Color.WHITE) { s.kicks.add(Kick(currentKicker(s), false)); save(); render() }))
        }
        card.addView(r)
        val tools = row().apply { setPadding(0, dp(7), 0, 0) }
        tools.addView(weighted(btn("↶ تراجع", soft(), ink(), 42) { if (s.kicks.isNotEmpty()) s.kicks.removeAt(s.kicks.lastIndex); save(); render() }))
        tools.addView(weighted(btn("↺ تصفير", soft(), red, 42) { s.kicks.clear(); s.kickOrder = ""; save(); render() }))
        card.addView(tools); footer(card, s)
        return card
    }

    private fun kickTrack(s: MatchState, team: String): String = s.kicks.filter { it.team == team }.joinToString(" ") { if (it.scored) "●" else "×" }.ifBlank { "○ ○ ○ ○ ○" }
    private fun currentKicker(s: MatchState): String {
        val first = if (s.kickOrder.isBlank()) "home" else s.kickOrder
        val second = if (first == "home") "away" else "home"
        return if (s.kicks.size % 2 == 0) first else second
    }
    private fun shootoutWinner(s: MatchState): String? {
        val hs = s.kicks.count { it.team == "home" && it.scored }; val ascore = s.kicks.count { it.team == "away" && it.scored }
        val ht = s.kicks.count { it.team == "home" }; val at = s.kicks.count { it.team == "away" }
        if (ht < 5 || at < 5) {
            if (hs > ascore + (5 - at)) return "home"
            if (ascore > hs + (5 - ht)) return "away"
            return null
        }
        return if (ht == at && hs != ascore) if (hs > ascore) "home" else "away" else null
    }

    private fun setsCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val finished = s.setsHome >= s.setsToWin || s.setsAway >= s.setsToWin
        card.addView(text(if (finished) "🏆 ${if (s.setsHome > s.setsAway) s.home else s.away}" else "الأشواط ${s.setsHome} - ${s.setsAway}", 13f, if (finished) green else muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0) })
        card.addView(text("${s.setPointsHome}  :  ${s.setPointsAway}", 40f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE })
        if (!finished) {
            val r = row()
            r.addView(weighted(btn("+ ${s.home}", green, Color.WHITE) { addSetPoint(s, "home") }))
            r.addView(weighted(btn("+ ${s.away}", blue, Color.WHITE) { addSetPoint(s, "away") }))
            card.addView(r)
        }
        if (s.setHistory.isNotEmpty()) card.addView(text(s.setHistory.mapIndexed { i, v -> "${i + 1}) ${v.home}-${v.away}" }.joinToString("   "), 11f, muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0) })
        val tools = row().apply { setPadding(0, dp(7), 0, 0) }
        tools.addView(weighted(btn("↶ تراجع", soft(), ink(), 42) { undoSetPoint(s) }))
        tools.addView(weighted(btn("↺ تصفير", soft(), red, 42) { s.setPointsHome = 0; s.setPointsAway = 0; s.setsHome = 0; s.setsAway = 0; s.setHistory.clear(); s.pointHistory.clear(); save(); render() }))
        card.addView(tools); footer(card, s)
        return card
    }

    private fun addSetPoint(s: MatchState, team: String) {
        s.pointHistory.add(team)
        if (team == "home") s.setPointsHome++ else s.setPointsAway++
        val setNo = s.setsHome + s.setsAway + 1
        val target = if (setNo >= s.setsToWin * 2 - 1) s.decidingPoints else s.pointsPerSet
        if (maxOf(s.setPointsHome, s.setPointsAway) >= target && abs(s.setPointsHome - s.setPointsAway) >= 2) {
            s.setHistory.add(SetResult(s.setPointsHome, s.setPointsAway))
            if (s.setPointsHome > s.setPointsAway) s.setsHome++ else s.setsAway++
            s.setPointsHome = 0; s.setPointsAway = 0; s.pointHistory.clear()
        }
        save(); render()
    }

    private fun undoSetPoint(s: MatchState) {
        if (s.pointHistory.isEmpty()) return
        val last = s.pointHistory.removeAt(s.pointHistory.lastIndex)
        if (last == "home") s.setPointsHome = (s.setPointsHome - 1).coerceAtLeast(0) else s.setPointsAway = (s.setPointsAway - 1).coerceAtLeast(0)
        save(); render()
    }

    private fun basketCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val top = row()
        top.addView(weighted(text(if (s.quarter <= 4) "الربع ${s.quarter}" else "إضافي ${s.quarter - 4}", 12f, muted(), true).apply { gravity = Gravity.CENTER }))
        val shot = text("24", 14f, amber, true).apply { gravity = Gravity.CENTER; background = shape(soft(), 99); setPadding(dp(12), dp(5), dp(12), dp(5)) }
        top.addView(shot); liveShots[s.id] = shot; card.addView(top)
        val time = text("10:00", 43f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, dp(7), 0, 0) }
        liveTimes[s.id] = time; card.addView(time)
        card.addView(scoreControls(s))
        val ctl = row()
        ctl.addView(weighted(btn(if (s.running) "⏸ إيقاف" else "▶ ابدأ", if (s.running) amber else green, Color.WHITE) { if (s.running) s.freeze() else if (s.basketballRemaining() > 0) s.resume(); save(); render() }))
        ctl.addView(weighted(btn("↻ 24 ثانية", soft(), amber) { resetShot(s); save(); render() }))
        card.addView(ctl)
        fun pointsRow(team: String, color: Int): LinearLayout = row().apply {
            setPadding(0, dp(7), 0, 0)
            for (p in 1..3) addView(weighted(btn("+$p ${if (team == "home") s.home else s.away}", soft(), color, 41) { addBasket(s, team, p) }))
        }
        card.addView(pointsRow("home", green)); card.addView(pointsRow("away", blue))
        val fouls = row().apply { setPadding(0, dp(7), 0, 0) }
        fouls.addView(weighted(btn("خطأ ${s.home}: ${s.foulsHome}${if (s.foulsHome >= 5) " BONUS" else ""}", soft(), if (s.foulsHome >= 5) red else ink(), 41) { s.foulsHome++; save(); render() }))
        fouls.addView(weighted(btn("خطأ ${s.away}: ${s.foulsAway}${if (s.foulsAway >= 5) " BONUS" else ""}", soft(), if (s.foulsAway >= 5) red else ink(), 41) { s.foulsAway++; save(); render() }))
        card.addView(fouls)
        val tools = row().apply { setPadding(0, dp(7), 0, 0) }
        tools.addView(weighted(btn("↶ تراجع نقطة", soft(), ink(), 41) { undoBasket(s) }))
        tools.addView(weighted(btn("الربع التالي", soft(), ink(), 41) { nextQuarter(s) }))
        card.addView(tools); footer(card, s)
        return card
    }

    private fun resetShot(s: MatchState) { s.shotElapsedMs = 0L; s.shotStartedAt = if (s.running) System.currentTimeMillis() else 0L }
    private fun addBasket(s: MatchState, team: String, p: Int) {
        if (team == "home") s.scoreHome += p else s.scoreAway += p
        s.basketHistory.add("$team:$p"); resetShot(s); save(); render()
    }
    private fun undoBasket(s: MatchState) {
        if (s.basketHistory.isEmpty()) return
        val parts = s.basketHistory.removeAt(s.basketHistory.lastIndex).split(":")
        val p = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (parts.firstOrNull() == "home") s.scoreHome = (s.scoreHome - p).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - p).coerceAtLeast(0)
        save(); render()
    }
    private fun nextQuarter(s: MatchState) {
        if (s.running) s.freeze(); s.quarter++; s.elapsedMs = 0; s.startedAt = 0; s.shotElapsedMs = 0; s.shotStartedAt = 0; s.foulsHome = 0; s.foulsAway = 0
        save(); render()
    }

    private fun footer(card: LinearLayout, s: MatchState) {
        val r = row().apply { setPadding(0, dp(9), 0, 0) }
        r.addView(weighted(btn("📄 تقرير", soft(), ink(), 41) { shareReport(s) }))
        r.addView(weighted(btn("⛶ تكبير", soft(), ink(), 41) { bigScreen(s) }))
        r.addView(weighted(btn(if (s.archived) "↩ استعادة" else "📦 أرشفة", soft(), if (s.archived) green else muted(), 41) { if (s.running) s.freeze(); s.archived = !s.archived; save(); render() }))
        card.addView(r)
    }

    private fun settings(s: MatchState) {
        val form = col().apply { setPadding(dp(30), 0, dp(30), 0) }
        val title = EditText(this).apply { hint = "اسم البطاقة"; setText(s.title) }
        val home = EditText(this).apply { hint = "الفريق الأول"; setText(s.home) }
        val away = EditText(this).apply { hint = "الفريق الثاني"; setText(s.away) }
        form.addView(title); form.addView(home); form.addView(away)
        var extra: EditText? = null
        if (s.type == MatchState.TYPE_MATCH || s.type == MatchState.TYPE_BASKETBALL || s.type == MatchState.TYPE_SETS) {
            extra = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                hint = when (s.type) { MatchState.TYPE_MATCH -> "مدة الشوط"; MatchState.TYPE_BASKETBALL -> "دقائق الربع"; else -> "نقاط الشوط" }
                setText(when (s.type) { MatchState.TYPE_MATCH -> s.halfMinutes; MatchState.TYPE_BASKETBALL -> s.quarterMinutes; else -> s.pointsPerSet }.toString())
            }
            form.addView(extra)
        }
        AlertDialog.Builder(this).setTitle("إعدادات").setView(form).setPositiveButton("حفظ") { _, _ ->
            s.title = title.text.toString().trim().ifBlank { s.title }; s.home = home.text.toString().trim().ifBlank { "الفريق ١" }; s.away = away.text.toString().trim().ifBlank { "الفريق ٢" }
            val v = extra?.text?.toString()?.toIntOrNull()
            if (v != null) when (s.type) { MatchState.TYPE_MATCH -> s.halfMinutes = v.coerceIn(1, 200); MatchState.TYPE_BASKETBALL -> s.quarterMinutes = v.coerceIn(1, 30); MatchState.TYPE_SETS -> s.pointsPerSet = v.coerceIn(1, 100) }
            save(); render()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun deleteConfirm(s: MatchState) {
        AlertDialog.Builder(this).setTitle("حذف البطاقة؟").setMessage("سيتم حذف ${s.title} نهائيًا").setPositiveButton("حذف") { _, _ -> states.remove(s); save(); render() }.setNegativeButton("إلغاء", null).show()
    }

    private fun resetMatch(s: MatchState) {
        AlertDialog.Builder(this).setTitle("تصفير المباراة؟").setPositiveButton("تصفير") { _, _ ->
            s.running = false; s.startedAt = 0; s.elapsedMs = 0; s.lostMs = 0; s.pausedAt = 0; s.scoreHome = 0; s.scoreAway = 0; s.events.clear(); save(); render()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun updateLive() {
        val now = System.currentTimeMillis()
        states.forEach { s ->
            when (s.type) {
                MatchState.TYPE_MATCH -> {
                    liveTimes[s.id]?.text = clock(s.currentElapsed(now), true)
                    liveLost[s.id]?.text = "الوقت الضائع ${clock(s.currentLost(now))}"
                }
                MatchState.TYPE_BASKETBALL -> {
                    val remain = s.basketballRemaining(now)
                    liveTimes[s.id]?.text = clock(remain)
                    val shot = ((s.shotRemaining(now) + 999) / 1000).toInt()
                    liveShots[s.id]?.text = shot.toString(); liveShots[s.id]?.setTextColor(if (shot <= 5) red else amber)
                    if (s.running && remain <= 0L) { s.freeze(now); save() }
                }
            }
        }
        val count = states.count { it.running && !it.archived }
        if (::active.isInitialized) active.text = "$count نشطة"
        if (count > 0) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun clock(ms: Long, tenth: Boolean = false): String {
        val safe = ms.coerceAtLeast(0L); val total = safe / 1000; val m = total / 60; val sec = total % 60
        return if (tenth) "%02d:%02d.%d".format(m, sec, (safe % 1000) / 100) else "%02d:%02d".format(m, sec)
    }

    private fun report(s: MatchState): String = buildString {
        appendLine(s.title); appendLine("${s.home} ${s.scoreHome} - ${s.scoreAway} ${s.away}")
        when (s.type) {
            MatchState.TYPE_MATCH -> { appendLine("الوقت: ${clock(s.currentElapsed())}"); appendLine("الوقت الضائع: ${clock(s.currentLost())}"); s.events.forEach { appendLine("${clock(it.timeMs)} — ${it.kind} — ${if (it.team == "home") s.home else s.away}") } }
            MatchState.TYPE_SHOOTOUT -> appendLine("الترجيح: ${s.kicks.count { it.team == "home" && it.scored }} - ${s.kicks.count { it.team == "away" && it.scored }}")
            MatchState.TYPE_SETS -> appendLine("الأشواط: ${s.setsHome} - ${s.setsAway}")
            MatchState.TYPE_BASKETBALL -> appendLine("الربع: ${s.quarter} — الأخطاء: ${s.foulsHome} - ${s.foulsAway}")
        }
    }

    private fun shareReport(s: MatchState) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report(s)); putExtra(Intent.EXTRA_SUBJECT, s.title) }, "مشاركة التقرير"))
    }

    private fun bigScreen(s: MatchState) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val box = col().apply { gravity = Gravity.CENTER; setPadding(dp(22), dp(22), dp(22), dp(22)); setBackgroundColor(0xFF050806.toInt()) }
        val close = btn("× إغلاق", 0xFF222A25.toInt(), Color.WHITE, 44) { d.dismiss() }; box.addView(close)
        box.addView(text("${s.home}          ${s.away}", 21f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(8)) })
        val t = text("", 68f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }; box.addView(t)
        val sc = text("", 55f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }; box.addView(sc)
        val updater = object : Runnable { override fun run() { if (!d.isShowing) return; t.text = when (s.type) { MatchState.TYPE_MATCH -> clock(s.currentElapsed()); MatchState.TYPE_BASKETBALL -> clock(s.basketballRemaining()); MatchState.TYPE_SETS -> "${s.setsHome} - ${s.setsAway}"; else -> "" }; sc.text = when (s.type) { MatchState.TYPE_SHOOTOUT -> "${s.kicks.count { it.team == "home" && it.scored }} : ${s.kicks.count { it.team == "away" && it.scored }}"; MatchState.TYPE_SETS -> "${s.setPointsHome} : ${s.setPointsAway}"; else -> "${s.scoreHome} : ${s.scoreAway}" }; handler.postDelayed(this, 250) } }
        d.setContentView(box); d.setOnDismissListener { handler.removeCallbacks(updater) }; d.show(); handler.post(updater)
    }

    private fun save() {
        try {
            val obj = JSONObject().apply { put("items", JSONArray().apply { states.forEach { put(it.toJson()) } }) }
            prefs.edit().putString("json", obj.toString()).putBoolean("dark", dark).apply()
        } catch (_: Exception) { }
    }

    private fun load() {
        dark = prefs.getBoolean("dark", false)
        val raw = prefs.getString("json", null) ?: return
        try {
            val arr = JSONObject(raw).optJSONArray("items") ?: return
            for (i in 0 until arr.length()) states.add(MatchState.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) { states.clear() }
    }

    private fun backup(): String = JSONObject().apply { put("app", "MatchClock"); put("items", JSONArray().apply { states.forEach { put(it.toJson()) } }) }.toString(2)
    private fun exportBackup() { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "matchclock-backup.json") }, 501) }
    private fun importBackup() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }, 502) }

    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 501) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup()) }
                Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 502) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                val arr = JSONObject(raw).optJSONArray("items") ?: return
                states.clear(); for (i in 0 until arr.length()) states.add(MatchState.fromJson(arr.getJSONObject(i)))
                save(); render(); Toast.makeText(this, "تم الاستيراد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "تعذر قراءة الملف", Toast.LENGTH_LONG).show() }
    }
}
