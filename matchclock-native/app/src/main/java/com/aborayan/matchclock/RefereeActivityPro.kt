package com.aborayan.matchclock

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class RefereeActivityPro : Activity() {

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
        buildShell()
        render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
    private fun bg(): Int = if (dark) 0xFF07110C.toInt() else 0xFFF5F7F4.toInt()
    private fun surface(): Int = if (dark) 0xFF132019.toInt() else Color.WHITE
    private fun ink(): Int = if (dark) 0xFFF4F8F5.toInt() else 0xFF142219.toInt()
    private fun muted(): Int = if (dark) 0xFFA7B8AE.toInt() else 0xFF627168.toInt()
    private fun soft(): Int = if (dark) 0xFF1B2B22.toInt() else 0xFFF0F3F0.toInt()
    private fun line(): Int = if (dark) 0xFF34463B.toInt() else 0xFFDCE4DE.toInt()
    private val green = 0xFF168449.toInt()
    private val greenDark = 0xFF0D6F3A.toInt()
    private val blue = 0xFF246FB7.toInt()
    private val amber = 0xFFD08B22.toInt()
    private val yellowCard = 0xFFF2C94C.toInt()
    private val red = 0xFFC94343.toInt()

    private fun latin(value: String): String = buildString(value.length) {
        value.forEach { c ->
            append(
                when (c) {
                    '٠', '۰' -> '0'; '١', '۱' -> '1'; '٢', '۲' -> '2'; '٣', '۳' -> '3'; '٤', '۴' -> '4'
                    '٥', '۵' -> '5'; '٦', '۶' -> '6'; '٧', '۷' -> '7'; '٨', '۸' -> '8'; '٩', '۹' -> '9'
                    else -> c
                }
            )
        }
    }

    private fun shape(color: Int, radius: Int = 16, stroke: Int? = null, strokeWidth: Int = 1): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(strokeWidth), stroke)
    }

    private fun text(value: String, size: Float = 14f, color: Int = ink(), bold: Boolean = false): TextView = TextView(this).apply {
        text = latin(value)
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(5), dp(4), dp(5), dp(4))
        includeFontPadding = false
    }

    private fun iconDrawable(resId: Int, tint: Int?, sizeDp: Int): Drawable? {
        val d = getDrawable(resId)?.mutate() ?: return null
        if (tint != null) d.setTint(tint)
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp))
        return d
    }

    private fun btn(
        label: String,
        fill: Int = soft(),
        color: Int = ink(),
        heightDp: Int = 58,
        iconRes: Int? = null,
        iconTint: Int? = color,
        iconSize: Int = 26,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = latin(label)
        isAllCaps = false
        textSize = 14f
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(heightDp)
        minimumHeight = dp(heightDp)
        minimumWidth = 0
        background = shape(fill, 15, if (fill == soft()) line() else null)
        setPadding(dp(12), 0, dp(12), 0)
        compoundDrawablePadding = dp(8)
        iconRes?.let { setCompoundDrawablesRelative(iconDrawable(it, iconTint, iconSize), null, null, null) }
        setOnClickListener { onClick() }
    }

    private fun iconBtn(
        resId: Int,
        tint: Int = ink(),
        fill: Int = soft(),
        sizeDp: Int = 52,
        iconSizeDp: Int = 28,
        content: String,
        onClick: () -> Unit
    ): ImageButton = ImageButton(this).apply {
        setImageResource(resId)
        drawable?.mutate()?.setTint(tint)
        scaleType = ImageView.ScaleType.CENTER
        background = shape(fill, 15, if (fill == soft()) line() else null)
        setPadding(dp((sizeDp - iconSizeDp) / 2), dp((sizeDp - iconSizeDp) / 2), dp((sizeDp - iconSizeDp) / 2), dp((sizeDp - iconSizeDp) / 2))
        contentDescription = content
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun row(gap: Int = 8): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = object : android.graphics.drawable.ColorDrawable(Color.TRANSPARENT) {
            override fun getIntrinsicWidth(): Int = dp(gap)
        }
    }

    private fun col(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun weighted(v: View): View = v.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }

    private fun applySystemBars() {
        root.setOnApplyWindowInsetsListener { v, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(left, top, right, bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun buildShell() {
        root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bg())
        }
        applySystemBars()

        val page = col().apply { setPadding(dp(14), dp(12), dp(14), dp(92)) }
        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val header = row(8)
        val appIcon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(appIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleBox = col()
        titleBox.addView(text("ساعات التوقيت", 20f, ink(), true))
        titleBox.addView(text("لوحة تحكيم سريعة وواضحة", 11.5f, muted()))
        header.addView(weighted(titleBox))
        active = text("0 نشطة", 11.5f, green, true).apply {
            gravity = Gravity.CENTER
            background = shape(if (dark) 0xFF163622.toInt() else 0xFFE7F5EC.toInt(), 99, green)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        header.addView(active)
        header.addView(iconBtn(R.drawable.ic_theme, ink(), soft(), 52, 28, "تغيير المظهر") {
            dark = !dark
            prefs.edit().putBoolean("dark", dark).apply()
            buildShell(); render()
        })
        header.addView(iconBtn(android.R.drawable.ic_menu_more, ink(), soft(), 52, 28, "القائمة") { showMenu() })
        page.addView(header)
        page.addView(text("الأرقام تظهر بالإنجليزية دائمًا، والبطاقات مصممة للمس السريع أثناء المباراة.", 12f, muted()).apply {
            setPadding(dp(4), dp(7), dp(4), dp(10))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val body = col()
        empty = text("لا توجد بطاقات بعد\nاضغط زر الإضافة لبدء مباراة جديدة", 16f, muted(), true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(12), dp(90), dp(12), dp(90))
        }
        body.addView(empty)
        grid = GridLayout(this).apply { alignmentMode = GridLayout.ALIGN_BOUNDS }
        body.addView(grid)
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val fab = iconBtn(android.R.drawable.ic_input_add, Color.WHITE, green, 68, 34, "إضافة بطاقة") { addCardDialog() }
        fab.elevation = dp(10).toFloat()
        root.addView(fab, FrameLayout.LayoutParams(dp(68), dp(68), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(18), dp(18), dp(18), dp(18))
        })

        window.statusBarColor = bg()
        window.navigationBarColor = bg()
        var flags = 0
        if (!dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
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
        val labels = arrayOf("ساعة مباراة", "ضربات ترجيح", "مباراة أشواط", "كرة سلة")
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
            states.add(MatchState(type = type, title = title, home = "الفريق 1", away = "الفريق 2"))
            save(); render()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun render() {
        if (!::grid.isInitialized) return
        grid.removeAllViews(); liveTimes.clear(); liveLost.clear(); liveShots.clear()
        val list = states.filter { showArchived || !it.archived }
        val columns = when {
            resources.configuration.screenWidthDp >= 1100 -> 3
            resources.configuration.screenWidthDp >= 720 -> 2
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
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(4), dp(8), dp(4), dp(8))
            }
            grid.addView(card, lp)
        }
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        updateLive()
    }

    private fun cardBase(s: MatchState, n: Int): LinearLayout = col().apply {
        setPadding(dp(18), dp(17), dp(18), dp(18))
        background = shape(surface(), 22, if (s.running) green else line(), if (s.running) 2 else 1)
        elevation = dp(4).toFloat()

        val h = row(7)
        h.addView(text("#${n.toString().padStart(2, '0')}", 11f, muted(), true).apply {
            gravity = Gravity.CENTER
            background = shape(soft(), 10)
            setPadding(dp(9), dp(7), dp(9), dp(7))
        })
        h.addView(weighted(text(latin(s.title), 17f, ink(), true).apply { setOnClickListener { settings(s) } }))
        h.addView(iconBtn(android.R.drawable.ic_menu_manage, ink(), soft(), 50, 26, "إعدادات") { settings(s) })
        h.addView(iconBtn(android.R.drawable.ic_menu_delete, red, soft(), 50, 26, "حذف") { deleteConfirm(s) })
        addView(h)
    }

    private fun matchCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val statusRow = row().apply { setPadding(0, dp(8), 0, 0) }
        statusRow.addView(weighted(text(if (s.running) "● جارٍ التوقيت" else "● متوقفة", 12.5f, if (s.running) green else muted(), true)))
        statusRow.addView(text(if (s.sport == "handball") "كرة يد" else "كرة قدم", 11.5f, muted(), true).apply {
            gravity = Gravity.CENTER
            background = shape(soft(), 99)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        card.addView(statusRow)

        val time = text("00:00.0", 56f, ink(), true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(11), 0, dp(4))
        }
        card.addView(time); liveTimes[s.id] = time
        val lost = text("الوقت الضائع 00:00", 13f, amber, true).apply { gravity = Gravity.CENTER }
        card.addView(lost); liveLost[s.id] = lost

        card.addView(scoreControls(s))

        val controls = row(10)
        controls.addView(weighted(btn(
            if (s.running) "إيقاف" else "ابدأ",
            if (s.running) amber else green,
            Color.WHITE,
            62,
            if (s.running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            Color.WHITE,
            28
        ) {
            if (s.running) s.freeze() else s.resume(); save(); render()
        }))
        controls.addView(weighted(btn("تصفير", soft(), ink(), 62, android.R.drawable.ic_menu_revert, ink(), 27) { resetMatch(s) }))
        card.addView(controls)

        val eventTitle = text("أحداث المباراة", 13f, ink(), true).apply { setPadding(0, dp(13), 0, dp(7)) }
        card.addView(eventTitle)
        val events = row(8)
        events.addView(weighted(btn("هدف", green, Color.WHITE, 66, R.drawable.ic_goal, null, 31) { addEvent(s, "goal") }))
        events.addView(weighted(btn("إنذار", yellowCard, 0xFF3B3218.toInt(), 66, R.drawable.ic_card, 0xFF3B3218.toInt(), 29) { addEvent(s, "yellow") }))
        events.addView(weighted(btn("طرد", red, Color.WHITE, 66, R.drawable.ic_card, Color.WHITE, 29) { addEvent(s, "red") }))
        card.addView(events)
        if (s.sport == "handball") {
            card.addView(btn("إيقاف دقيقتين", soft(), amber, 58, android.R.drawable.ic_lock_idle_alarm, amber, 27) { addEvent(s, "suspension") }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            })
        }

        card.addView(eventLogSection(s))
        footer(card, s)
        return card
    }

    private fun scoreControls(s: MatchState): View {
        val box = row(12).apply {
            setPadding(0, dp(12), 0, dp(12))
            background = shape(soft(), 18)
        }
        box.addView(weighted(teamBox(s, true)))
        box.addView(text(":", 28f, muted(), true).apply { gravity = Gravity.CENTER })
        box.addView(weighted(teamBox(s, false)))
        return box
    }

    private fun teamBox(s: MatchState, homeTeam: Boolean): View {
        val box = col().apply { gravity = Gravity.CENTER }
        box.addView(text(latin(if (homeTeam) s.home else s.away), 14f, ink(), true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        val r = row(7)
        r.addView(iconBtn(android.R.drawable.ic_media_rew, ink(), surface(), 52, 24, "إنقاص") {
            if (homeTeam) s.scoreHome = (s.scoreHome - 1).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - 1).coerceAtLeast(0)
            save(); render()
        }.apply {
            setImageDrawable(null)
            val minus = text("−", 28f, ink(), true).apply { gravity = Gravity.CENTER }
            foreground = null
        })
        val score = text((if (homeTeam) s.scoreHome else s.scoreAway).toString(), 36f, ink(), true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            minWidth = dp(48)
        }
        r.addView(score)
        val plus = Button(this).apply {
            text = "+"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            minWidth = dp(52); minimumWidth = dp(52); minHeight = dp(52); minimumHeight = dp(52)
            background = shape(if (homeTeam) green else blue, 15)
            setOnClickListener {
                if (homeTeam) s.scoreHome++ else s.scoreAway++
                save(); render()
            }
        }
        val minus = Button(this).apply {
            text = "−"
            textSize = 26f
            setTextColor(ink())
            setTypeface(typeface, Typeface.BOLD)
            minWidth = dp(52); minimumWidth = dp(52); minHeight = dp(52); minimumHeight = dp(52)
            background = shape(surface(), 15, line())
            setOnClickListener {
                if (homeTeam) s.scoreHome = (s.scoreHome - 1).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - 1).coerceAtLeast(0)
                save(); render()
            }
        }
        r.removeAllViews()
        r.addView(minus, LinearLayout.LayoutParams(dp(52), dp(52)))
        r.addView(score)
        r.addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)))
        box.addView(r)
        return box
    }

    private fun eventName(kind: String): String = when (kind) {
        "goal" -> "هدف"
        "yellow" -> "إنذار"
        "red" -> "طرد"
        "suspension" -> "إيقاف دقيقتين"
        else -> kind
    }

    private fun eventColor(kind: String): Int = when (kind) {
        "goal" -> green
        "yellow" -> amber
        "red" -> red
        "suspension" -> blue
        else -> muted()
    }

    private fun eventIcon(kind: String): Int = when (kind) {
        "goal" -> R.drawable.ic_goal
        "yellow", "red" -> R.drawable.ic_card
        else -> android.R.drawable.ic_lock_idle_alarm
    }

    private fun addEvent(s: MatchState, kind: String) {
        val wrap = col().apply { setPadding(dp(22), dp(6), dp(22), 0) }
        wrap.addView(text("الفريق", 12f, muted(), true))
        val team = Spinner(this).apply {
            adapter = ArrayAdapter(this@RefereeActivityPro, android.R.layout.simple_spinner_dropdown_item, arrayOf(latin(s.home), latin(s.away)))
            minimumHeight = dp(54)
        }
        wrap.addView(team, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        wrap.addView(text("اسم اللاعب", 12f, muted(), true).apply { setPadding(dp(4), dp(10), dp(4), dp(3)) })
        val player = EditText(this).apply {
            hint = "مثال: محمد"
            textDirection = View.TEXT_DIRECTION_RTL
            inputType = InputType.TYPE_CLASS_TEXT
            minHeight = dp(56)
        }
        wrap.addView(player)
        wrap.addView(text("رقم اللاعب", 12f, muted(), true).apply { setPadding(dp(4), dp(10), dp(4), dp(3)) })
        val number = EditText(this).apply {
            hint = "مثال: 7"
            inputType = InputType.TYPE_CLASS_NUMBER
            textDirection = View.TEXT_DIRECTION_LTR
            minHeight = dp(56)
        }
        wrap.addView(number)

        val dialog = AlertDialog.Builder(this)
            .setTitle("تسجيل ${eventName(kind)}")
            .setView(wrap)
            .setPositiveButton("تسجيل", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val playerName = latin(player.text.toString().trim())
                val playerNumber = latin(number.text.toString().trim())
                if (playerName.isBlank()) {
                    player.error = "أدخل اسم اللاعب"
                    player.requestFocus()
                    return@setOnClickListener
                }
                if (playerNumber.isBlank()) {
                    number.error = "أدخل رقم اللاعب"
                    number.requestFocus()
                    return@setOnClickListener
                }
                val teamKey = if (team.selectedItemPosition == 0) "home" else "away"
                if (s.autoPause && s.running) s.freeze()
                if (kind == "goal") {
                    if (teamKey == "home") s.scoreHome++ else s.scoreAway++
                }
                s.events.add(MatchEvent(s.currentElapsed(), kind, teamKey, playerName, playerNumber))
                save(); render(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun eventLogSection(s: MatchState): View {
        val outer = col().apply {
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = shape(if (dark) 0xFF0F1A14.toInt() else 0xFFFAFBFA.toInt(), 17, line())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        val header = row()
        header.addView(weighted(text("سجل أحداث المباراة", 13.5f, ink(), true)))
        header.addView(text("${s.events.size} حدث", 11f, muted(), true).apply {
            gravity = Gravity.CENTER
            background = shape(soft(), 99)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        })
        outer.addView(header)

        if (s.events.isEmpty()) {
            outer.addView(text("لا توجد أحداث مسجلة حتى الآن", 12f, muted()).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(18), dp(6), dp(14))
            })
            return outer
        }

        s.events.takeLast(5).asReversed().forEach { outer.addView(eventRow(s, it)) }
        if (s.events.size > 5) {
            outer.addView(btn("عرض السجل الكامل (${s.events.size})", soft(), ink(), 52, android.R.drawable.ic_menu_view, ink(), 24) { showFullLog(s) }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            })
        }
        return outer
    }

    private fun eventRow(s: MatchState, e: MatchEvent): View {
        val r = row(9).apply {
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = shape(soft(), 13)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) }
        }
        val iconBox = FrameLayout(this).apply { background = shape(eventColor(e.kind), 12) }
        val iv = ImageView(this).apply {
            setImageResource(eventIcon(e.kind))
            scaleType = ImageView.ScaleType.CENTER
            if (e.kind != "goal") drawable?.mutate()?.setTint(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        iconBox.addView(iv, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        r.addView(iconBox, LinearLayout.LayoutParams(dp(42), dp(42)))

        val info = col()
        val teamName = latin(if (e.team == "home") s.home else s.away)
        info.addView(text("${eventName(e.kind)} • $teamName", 12.5f, eventColor(e.kind), true))
        val playerLine = buildString {
            append(latin(e.player).ifBlank { "لاعب غير محدد" })
            if (e.number.isNotBlank()) append("  #${latin(e.number)}")
        }
        info.addView(text(playerLine, 12.5f, ink(), true))
        r.addView(weighted(info))
        r.addView(text(clock(e.timeMs), 12f, muted(), true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            background = shape(surface(), 10, line())
            setPadding(dp(8), dp(7), dp(8), dp(7))
        })
        return r
    }

    private fun showFullLog(s: MatchState) {
        val body = col().apply { setPadding(dp(16), dp(8), dp(16), dp(12)) }
        s.events.asReversed().forEach { body.addView(eventRow(s, it)) }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle("سجل ${latin(s.title)} — ${s.events.size} حدث")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun shootoutCard(s: MatchState, n: Int): View {
        val card = cardBase(s, n)
        val hs = s.kicks.count { it.team == "home" && it.scored }
        val ascore = s.kicks.count { it.team == "away" && it.scored }
        card.addView(text("$hs  :  $ascore", 52f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, dp(14), 0, dp(7)) })
        val winner = shootoutWinner(s)
        card.addView(text(when {
            winner != null -> "الفائز: ${if (winner == "home") latin(s.home) else latin(s.away)}"
            s.kickOrder.isBlank() -> "اختر الفريق الذي يبدأ"
            else -> "الدور: ${if (currentKicker(s) == "home") latin(s.home) else latin(s.away)}"
        }, 13f, if (winner != null) green else muted(), true).apply { gravity = Gravity.CENTER })
        card.addView(text("${latin(s.home)}: ${kickTrack(s, "home")}", 13f, green, true).apply { gravity = Gravity.CENTER })
        card.addView(text("${latin(s.away)}: ${kickTrack(s, "away")}", 13f, blue, true).apply { gravity = Gravity.CENTER })
        val r = row(10).apply { setPadding(0, dp(12), 0, 0) }
        if (s.kickOrder.isBlank()) {
            r.addView(weighted(btn("${latin(s.home)} يبدأ", green, Color.WHITE, 60) { s.kickOrder = "home"; save(); render() }))
            r.addView(weighted(btn("${latin(s.away)} يبدأ", blue, Color.WHITE, 60) { s.kickOrder = "away"; save(); render() }))
        } else if (winner == null) {
            r.addView(weighted(btn("هدف", green, Color.WHITE, 64, R.drawable.ic_goal, null, 30) { s.kicks.add(Kick(currentKicker(s), true)); save(); render() }))
            r.addView(weighted(btn("إهدار", red, Color.WHITE, 64, android.R.drawable.ic_menu_close_clear_cancel, Color.WHITE, 28) { s.kicks.add(Kick(currentKicker(s), false)); save(); render() }))
        }
        card.addView(r)
        val tools = row(10).apply { setPadding(0, dp(10), 0, 0) }
        tools.addView(weighted(btn("تراجع", soft(), ink(), 56, android.R.drawable.ic_menu_revert, ink(), 25) { if (s.kicks.isNotEmpty()) s.kicks.removeAt(s.kicks.lastIndex); save(); render() }))
        tools.addView(weighted(btn("تصفير", soft(), red, 56, android.R.drawable.ic_menu_delete, red, 25) { s.kicks.clear(); s.kickOrder = ""; save(); render() }))
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
        val hs = s.kicks.count { it.team == "home" && it.scored }
        val ascore = s.kicks.count { it.team == "away" && it.scored }
        val ht = s.kicks.count { it.team == "home" }
        val at = s.kicks.count { it.team == "away" }
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
        card.addView(text(if (finished) "الفائز: ${if (s.setsHome > s.setsAway) latin(s.home) else latin(s.away)}" else "الأشواط ${s.setsHome} - ${s.setsAway}", 14f, if (finished) green else muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(4)) })
        card.addView(text("${s.setPointsHome}  :  ${s.setPointsAway}", 52f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE })
        if (!finished) {
            val r = row(10)
            r.addView(weighted(btn("+ ${latin(s.home)}", green, Color.WHITE, 62) { addSetPoint(s, "home") }))
            r.addView(weighted(btn("+ ${latin(s.away)}", blue, Color.WHITE, 62) { addSetPoint(s, "away") }))
            card.addView(r)
        }
        if (s.setHistory.isNotEmpty()) {
            card.addView(text(s.setHistory.mapIndexed { i, v -> "${i + 1}) ${v.home}-${v.away}" }.joinToString("   "), 12f, muted(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        }
        val tools = row(10).apply { setPadding(0, dp(10), 0, 0) }
        tools.addView(weighted(btn("تراجع", soft(), ink(), 56, android.R.drawable.ic_menu_revert, ink(), 25) { undoSetPoint(s) }))
        tools.addView(weighted(btn("تصفير", soft(), red, 56, android.R.drawable.ic_menu_delete, red, 25) {
            s.setPointsHome = 0; s.setPointsAway = 0; s.setsHome = 0; s.setsAway = 0; s.setHistory.clear(); s.pointHistory.clear(); save(); render()
        }))
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
        top.addView(weighted(text(if (s.quarter <= 4) "الربع ${s.quarter}" else "إضافي ${s.quarter - 4}", 13f, muted(), true).apply { gravity = Gravity.CENTER }))
        val shot = text("24", 16f, amber, true).apply {
            gravity = Gravity.CENTER
            background = shape(soft(), 99)
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        top.addView(shot); liveShots[s.id] = shot; card.addView(top)
        val time = text("10:00", 54f, ink(), true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; setPadding(0, dp(10), 0, dp(5)) }
        liveTimes[s.id] = time; card.addView(time)
        card.addView(scoreControls(s))
        val ctl = row(10)
        ctl.addView(weighted(btn(if (s.running) "إيقاف" else "ابدأ", if (s.running) amber else green, Color.WHITE, 62, if (s.running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, Color.WHITE, 28) {
            if (s.running) s.freeze() else if (s.basketballRemaining() > 0) s.resume(); save(); render()
        }))
        ctl.addView(weighted(btn("24 ثانية", soft(), amber, 62, android.R.drawable.ic_lock_idle_alarm, amber, 27) { resetShot(s); save(); render() }))
        card.addView(ctl)

        fun pointsRow(team: String, color: Int): LinearLayout = row(8).apply {
            setPadding(0, dp(9), 0, 0)
            for (p in 1..3) addView(weighted(btn("+$p ${latin(if (team == "home") s.home else s.away)}", soft(), color, 54) { addBasket(s, team, p) }))
        }
        card.addView(pointsRow("home", green)); card.addView(pointsRow("away", blue))
        val fouls = row(8).apply { setPadding(0, dp(9), 0, 0) }
        fouls.addView(weighted(btn("خطأ ${latin(s.home)}: ${s.foulsHome}${if (s.foulsHome >= 5) " BONUS" else ""}", soft(), if (s.foulsHome >= 5) red else ink(), 54) { s.foulsHome++; save(); render() }))
        fouls.addView(weighted(btn("خطأ ${latin(s.away)}: ${s.foulsAway}${if (s.foulsAway >= 5) " BONUS" else ""}", soft(), if (s.foulsAway >= 5) red else ink(), 54) { s.foulsAway++; save(); render() }))
        card.addView(fouls)
        val tools = row(8).apply { setPadding(0, dp(9), 0, 0) }
        tools.addView(weighted(btn("تراجع نقطة", soft(), ink(), 54, android.R.drawable.ic_menu_revert, ink(), 24) { undoBasket(s) }))
        tools.addView(weighted(btn("الربع التالي", soft(), ink(), 54, android.R.drawable.ic_media_next, ink(), 24) { nextQuarter(s) }))
        card.addView(tools); footer(card, s)
        return card
    }

    private fun resetShot(s: MatchState) {
        s.shotElapsedMs = 0L
        s.shotStartedAt = if (s.running) System.currentTimeMillis() else 0L
    }

    private fun addBasket(s: MatchState, team: String, p: Int) {
        if (team == "home") s.scoreHome += p else s.scoreAway += p
        s.basketHistory.add("$team:$p")
        resetShot(s); save(); render()
    }

    private fun undoBasket(s: MatchState) {
        if (s.basketHistory.isEmpty()) return
        val parts = s.basketHistory.removeAt(s.basketHistory.lastIndex).split(":")
        val p = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (parts.firstOrNull() == "home") s.scoreHome = (s.scoreHome - p).coerceAtLeast(0) else s.scoreAway = (s.scoreAway - p).coerceAtLeast(0)
        save(); render()
    }

    private fun nextQuarter(s: MatchState) {
        if (s.running) s.freeze()
        s.quarter++
        s.elapsedMs = 0; s.startedAt = 0; s.shotElapsedMs = 0; s.shotStartedAt = 0; s.foulsHome = 0; s.foulsAway = 0
        save(); render()
    }

    private fun footer(card: LinearLayout, s: MatchState) {
        val r = row(8).apply { setPadding(0, dp(12), 0, 0) }
        r.addView(weighted(btn("تقرير", soft(), ink(), 56, android.R.drawable.ic_menu_share, ink(), 25) { shareReport(s) }))
        r.addView(weighted(btn("تكبير", soft(), ink(), 56, R.drawable.ic_fullscreen, ink(), 26) { bigScreen(s) }))
        r.addView(weighted(btn(if (s.archived) "استعادة" else "أرشفة", soft(), if (s.archived) green else muted(), 56, R.drawable.ic_archive, if (s.archived) green else muted(), 25) {
            if (s.running) s.freeze(); s.archived = !s.archived; save(); render()
        }))
        card.addView(r)
    }

    private fun settings(s: MatchState) {
        val form = col().apply { setPadding(dp(24), 0, dp(24), 0) }
        val title = EditText(this).apply { hint = "اسم البطاقة"; setText(latin(s.title)); minHeight = dp(54) }
        val home = EditText(this).apply { hint = "الفريق الأول"; setText(latin(s.home)); minHeight = dp(54) }
        val away = EditText(this).apply { hint = "الفريق الثاني"; setText(latin(s.away)); minHeight = dp(54) }
        form.addView(title); form.addView(home); form.addView(away)
        var extra: EditText? = null
        if (s.type == MatchState.TYPE_MATCH || s.type == MatchState.TYPE_BASKETBALL || s.type == MatchState.TYPE_SETS) {
            extra = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = when (s.type) { MatchState.TYPE_MATCH -> "مدة الشوط"; MatchState.TYPE_BASKETBALL -> "دقائق الربع"; else -> "نقاط الشوط" }
                setText(when (s.type) { MatchState.TYPE_MATCH -> s.halfMinutes; MatchState.TYPE_BASKETBALL -> s.quarterMinutes; else -> s.pointsPerSet }.toString())
                minHeight = dp(54)
                textDirection = View.TEXT_DIRECTION_LTR
            }
            form.addView(extra)
        }
        AlertDialog.Builder(this).setTitle("إعدادات البطاقة").setView(form).setPositiveButton("حفظ") { _, _ ->
            s.title = latin(title.text.toString().trim()).ifBlank { s.title }
            s.home = latin(home.text.toString().trim()).ifBlank { "الفريق 1" }
            s.away = latin(away.text.toString().trim()).ifBlank { "الفريق 2" }
            val v = latin(extra?.text?.toString().orEmpty()).toIntOrNull()
            if (v != null) when (s.type) {
                MatchState.TYPE_MATCH -> s.halfMinutes = v.coerceIn(1, 200)
                MatchState.TYPE_BASKETBALL -> s.quarterMinutes = v.coerceIn(1, 30)
                MatchState.TYPE_SETS -> s.pointsPerSet = v.coerceIn(1, 100)
            }
            save(); render()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun deleteConfirm(s: MatchState) {
        AlertDialog.Builder(this).setTitle("حذف البطاقة؟").setMessage("سيتم حذف ${latin(s.title)} نهائيًا")
            .setPositiveButton("حذف") { _, _ -> states.remove(s); save(); render() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun resetMatch(s: MatchState) {
        AlertDialog.Builder(this).setTitle("تصفير المباراة؟").setMessage("سيتم تصفير الوقت والنتيجة والسجل.")
            .setPositiveButton("تصفير") { _, _ ->
                s.running = false; s.startedAt = 0; s.elapsedMs = 0; s.lostMs = 0; s.pausedAt = 0
                s.scoreHome = 0; s.scoreAway = 0; s.events.clear(); save(); render()
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
                    liveShots[s.id]?.text = shot.toString()
                    liveShots[s.id]?.setTextColor(if (shot <= 5) red else amber)
                    if (s.running && remain <= 0L) { s.freeze(now); save() }
                }
            }
        }
        val count = states.count { it.running && !it.archived }
        if (::active.isInitialized) active.text = "$count نشطة"
        if (count > 0) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun clock(ms: Long, tenth: Boolean = false): String {
        val safe = ms.coerceAtLeast(0L)
        val total = safe / 1000
        val m = total / 60
        val sec = total % 60
        return if (tenth) String.format(Locale.US, "%02d:%02d.%d", m, sec, (safe % 1000) / 100)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    private fun report(s: MatchState): String = buildString {
        appendLine(latin(s.title))
        appendLine("${latin(s.home)} ${s.scoreHome} - ${s.scoreAway} ${latin(s.away)}")
        when (s.type) {
            MatchState.TYPE_MATCH -> {
                appendLine("الوقت: ${clock(s.currentElapsed())}")
                appendLine("الوقت الضائع: ${clock(s.currentLost())}")
                if (s.events.isNotEmpty()) appendLine("سجل الأحداث:")
                s.events.forEach {
                    val team = latin(if (it.team == "home") s.home else s.away)
                    appendLine("${clock(it.timeMs)} — ${eventName(it.kind)} — $team — ${latin(it.player)} #${latin(it.number)}")
                }
            }
            MatchState.TYPE_SHOOTOUT -> appendLine("الترجيح: ${s.kicks.count { it.team == "home" && it.scored }} - ${s.kicks.count { it.team == "away" && it.scored }}")
            MatchState.TYPE_SETS -> appendLine("الأشواط: ${s.setsHome} - ${s.setsAway}")
            MatchState.TYPE_BASKETBALL -> appendLine("الربع: ${s.quarter} — الأخطاء: ${s.foulsHome} - ${s.foulsAway}")
        }
    }

    private fun shareReport(s: MatchState) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report(s))
            putExtra(Intent.EXTRA_SUBJECT, latin(s.title))
        }, "مشاركة التقرير"))
    }

    private fun bigScreen(s: MatchState) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val box = col().apply {
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(34), dp(28), dp(34))
            setBackgroundColor(0xFF030705.toInt())
        }
        box.addView(btn("إغلاق", 0xFF202923.toInt(), Color.WHITE, 54, android.R.drawable.ic_menu_close_clear_cancel, Color.WHITE, 25) { d.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        box.addView(text("${latin(s.home)}          ${latin(s.away)}", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(36), 0, dp(10)) })
        val t = text("", 74f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        val sc = text("", 62f, Color.WHITE, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        box.addView(t); box.addView(sc)
        val updater = object : Runnable {
            override fun run() {
                if (!d.isShowing) return
                t.text = when (s.type) {
                    MatchState.TYPE_MATCH -> clock(s.currentElapsed())
                    MatchState.TYPE_BASKETBALL -> clock(s.basketballRemaining())
                    MatchState.TYPE_SETS -> "${s.setsHome} - ${s.setsAway}"
                    else -> ""
                }
                sc.text = when (s.type) {
                    MatchState.TYPE_SHOOTOUT -> "${s.kicks.count { it.team == "home" && it.scored }} : ${s.kicks.count { it.team == "away" && it.scored }}"
                    MatchState.TYPE_SETS -> "${s.setPointsHome} : ${s.setPointsAway}"
                    else -> "${s.scoreHome} : ${s.scoreAway}"
                }
                handler.postDelayed(this, 250)
            }
        }
        d.setContentView(box)
        d.setOnDismissListener { handler.removeCallbacks(updater) }
        d.show(); handler.post(updater)
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

    private fun backup(): String = JSONObject().apply {
        put("app", "MatchClock")
        put("version", "1.1.0")
        put("items", JSONArray().apply { states.forEach { put(it.toJson()) } })
    }.toString(2)

    private fun exportBackup() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "matchclock-backup-v1.1.0.json")
        }, 501)
    }

    private fun importBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, 502)
    }

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
                states.clear()
                for (i in 0 until arr.length()) states.add(MatchState.fromJson(arr.getJSONObject(i)))
                save(); render()
                Toast.makeText(this, "تم الاستيراد", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر قراءة الملف", Toast.LENGTH_LONG).show()
        }
    }
}
