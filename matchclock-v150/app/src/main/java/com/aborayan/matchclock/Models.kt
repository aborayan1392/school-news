package com.aborayan.matchclock

import org.json.JSONArray
import org.json.JSONObject

data class MatchEvent(
    var timeMs: Long = 0L,
    var kind: String = "",
    var team: String = "home",
    var player: String = "",
    var number: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("timeMs", timeMs)
        put("kind", kind)
        put("team", team)
        put("player", player)
        put("number", number)
    }

    companion object {
        fun fromJson(o: JSONObject) = MatchEvent(
            timeMs = o.optLong("timeMs"),
            kind = o.optString("kind"),
            team = o.optString("team", "home"),
            player = o.optString("player"),
            number = o.optString("number")
        )
    }
}

data class MatchState(
    var id: Long = System.currentTimeMillis(),
    var type: String = TYPE_MATCH,
    var title: String = "مباراة",
    var home: String = "الفريق الأول",
    var away: String = "الفريق الثاني",
    var scoreHome: Int = 0,
    var scoreAway: Int = 0,
    var elapsedMs: Long = 0L,
    var running: Boolean = false,
    var startedAt: Long = 0L,
    var lostMs: Long = 0L,
    var pausedAt: Long = 0L,
    var sport: String = "football",
    var halfMinutes: Int = 45,
    var autoPause: Boolean = true,
    var archived: Boolean = false,
    var notes: String = "",
    var referee: String = "",
    var events: MutableList<MatchEvent> = mutableListOf()
) {
    fun currentElapsed(now: Long = System.currentTimeMillis()): Long =
        if (running && startedAt > 0L) elapsedMs + (now - startedAt).coerceAtLeast(0L) else elapsedMs

    fun currentLost(now: Long = System.currentTimeMillis()): Long {
        val current = currentElapsed(now)
        val cap = halfMinutes.coerceAtLeast(1) * 60_000L
        return if (!running && pausedAt > 0L && current < cap) {
            lostMs + (now - pausedAt).coerceAtLeast(0L)
        } else {
            lostMs
        }
    }

    fun freeze(now: Long = System.currentTimeMillis()) {
        if (!running) return
        elapsedMs += (now - startedAt).coerceAtLeast(0L)
        running = false
        startedAt = 0L
        pausedAt = now
    }

    fun resume(now: Long = System.currentTimeMillis()) {
        if (running) return
        if (pausedAt > 0L) {
            val cap = halfMinutes.coerceAtLeast(1) * 60_000L
            if (elapsedMs < cap) lostMs += (now - pausedAt).coerceAtLeast(0L)
            pausedAt = 0L
        }
        startedAt = now
        running = true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("title", title)
        put("home", home)
        put("away", away)
        put("scoreHome", scoreHome)
        put("scoreAway", scoreAway)
        put("elapsedMs", currentElapsed())
        put("running", running)
        put("savedAt", System.currentTimeMillis())
        put("lostMs", currentLost())
        put("pausedAt", if (running) 0L else pausedAt)
        put("sport", sport)
        put("halfMinutes", halfMinutes)
        put("autoPause", autoPause)
        put("archived", archived)
        put("notes", notes)
        put("referee", referee)
        put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
    }

    companion object {
        const val TYPE_MATCH = "match"
        const val TYPE_SHOOTOUT = "shootout"
        const val TYPE_SETS = "sets"
        const val TYPE_BASKETBALL = "basketball"

        fun fromJson(o: JSONObject): MatchState {
            val s = MatchState(
                id = o.optLong("id", System.currentTimeMillis()),
                type = o.optString("type", TYPE_MATCH),
                title = o.optString("title", "مباراة"),
                home = o.optString("home", "الفريق الأول"),
                away = o.optString("away", "الفريق الثاني"),
                scoreHome = o.optInt("scoreHome"),
                scoreAway = o.optInt("scoreAway"),
                elapsedMs = o.optLong("elapsedMs"),
                running = o.optBoolean("running"),
                lostMs = o.optLong("lostMs"),
                pausedAt = o.optLong("pausedAt"),
                sport = o.optString("sport", "football"),
                halfMinutes = o.optInt("halfMinutes", 45),
                autoPause = o.optBoolean("autoPause", true),
                archived = o.optBoolean("archived"),
                notes = o.optString("notes"),
                referee = o.optString("referee")
            )
            val savedAt = o.optLong("savedAt", System.currentTimeMillis())
            if (s.running) s.startedAt = savedAt
            val ev = o.optJSONArray("events") ?: JSONArray()
            for (i in 0 until ev.length()) {
                val item = ev.optJSONObject(i) ?: continue
                s.events.add(MatchEvent.fromJson(item))
            }
            return s
        }
    }
}
