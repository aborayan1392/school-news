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
        put("timeMs", timeMs); put("kind", kind); put("team", team)
        put("player", player); put("number", number)
    }
    companion object {
        fun fromJson(o: JSONObject) = MatchEvent(
            o.optLong("timeMs"), o.optString("kind"), o.optString("team", "home"),
            o.optString("player"), o.optString("number")
        )
    }
}

data class Kick(var team: String = "home", var scored: Boolean = false) {
    fun toJson() = JSONObject().apply { put("team", team); put("scored", scored) }
    companion object { fun fromJson(o: JSONObject) = Kick(o.optString("team", "home"), o.optBoolean("scored")) }
}

data class SetResult(var home: Int = 0, var away: Int = 0) {
    fun toJson() = JSONObject().apply { put("home", home); put("away", away) }
    companion object { fun fromJson(o: JSONObject) = SetResult(o.optInt("home"), o.optInt("away")) }
}

data class MatchState(
    var id: Long = System.currentTimeMillis(),
    var type: String = TYPE_MATCH,
    var title: String = "مباراة",
    var home: String = "الفريق ١",
    var away: String = "الفريق ٢",
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
    var kicks: MutableList<Kick> = mutableListOf(),
    var kickOrder: String = "",
    var setPointsHome: Int = 0,
    var setPointsAway: Int = 0,
    var setsHome: Int = 0,
    var setsAway: Int = 0,
    var setsToWin: Int = 3,
    var pointsPerSet: Int = 25,
    var decidingPoints: Int = 15,
    var setHistory: MutableList<SetResult> = mutableListOf(),
    var pointHistory: MutableList<String> = mutableListOf(),
    var quarter: Int = 1,
    var quarterMinutes: Int = 10,
    var shotElapsedMs: Long = 0L,
    var shotStartedAt: Long = 0L,
    var foulsHome: Int = 0,
    var foulsAway: Int = 0,
    var basketHistory: MutableList<String> = mutableListOf(),
    var events: MutableList<MatchEvent> = mutableListOf()
) {
    fun currentElapsed(now: Long = System.currentTimeMillis()): Long =
        if (running && startedAt > 0L) elapsedMs + (now - startedAt).coerceAtLeast(0L) else elapsedMs

    fun currentLost(now: Long = System.currentTimeMillis()): Long {
        val current = currentElapsed(now)
        val cap = halfMinutes.coerceAtLeast(1) * 60_000L
        return if (!running && pausedAt > 0L && current < cap) lostMs + (now - pausedAt).coerceAtLeast(0L) else lostMs
    }

    fun basketballRemaining(now: Long = System.currentTimeMillis()): Long =
        (quarterMinutes.coerceAtLeast(1) * 60_000L - currentElapsed(now)).coerceAtLeast(0L)

    fun shotRemaining(now: Long = System.currentTimeMillis()): Long {
        val consumed = shotElapsedMs + if (running && shotStartedAt > 0L) (now - shotStartedAt).coerceAtLeast(0L) else 0L
        return (24_000L - consumed).coerceAtLeast(0L)
    }

    fun freeze(now: Long = System.currentTimeMillis()) {
        if (!running) return
        val delta = (now - startedAt).coerceAtLeast(0L)
        elapsedMs += delta
        if (type == TYPE_BASKETBALL && shotStartedAt > 0L) shotElapsedMs += (now - shotStartedAt).coerceAtLeast(0L)
        running = false
        startedAt = 0L
        shotStartedAt = 0L
        if (type == TYPE_MATCH) pausedAt = now
    }

    fun resume(now: Long = System.currentTimeMillis()) {
        if (running) return
        if (type == TYPE_MATCH && pausedAt > 0L) {
            val cap = halfMinutes.coerceAtLeast(1) * 60_000L
            if (elapsedMs < cap) lostMs += (now - pausedAt).coerceAtLeast(0L)
            pausedAt = 0L
        }
        startedAt = now
        running = true
        if (type == TYPE_BASKETBALL) shotStartedAt = now
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type); put("title", title); put("home", home); put("away", away)
        put("scoreHome", scoreHome); put("scoreAway", scoreAway); put("elapsedMs", currentElapsed())
        put("running", running); put("savedAt", System.currentTimeMillis()); put("lostMs", currentLost()); put("pausedAt", if (running) 0L else pausedAt)
        put("sport", sport); put("halfMinutes", halfMinutes); put("autoPause", autoPause); put("archived", archived)
        put("notes", notes); put("referee", referee); put("kickOrder", kickOrder)
        put("setPointsHome", setPointsHome); put("setPointsAway", setPointsAway); put("setsHome", setsHome); put("setsAway", setsAway)
        put("setsToWin", setsToWin); put("pointsPerSet", pointsPerSet); put("decidingPoints", decidingPoints)
        put("quarter", quarter); put("quarterMinutes", quarterMinutes); put("shotElapsedMs", if (running && type == TYPE_BASKETBALL) 24_000L - shotRemaining() else shotElapsedMs)
        put("foulsHome", foulsHome); put("foulsAway", foulsAway)
        put("kicks", JSONArray().apply { kicks.forEach { put(it.toJson()) } })
        put("setHistory", JSONArray().apply { setHistory.forEach { put(it.toJson()) } })
        put("pointHistory", JSONArray().apply { pointHistory.forEach { put(it) } })
        put("basketHistory", JSONArray().apply { basketHistory.forEach { put(it) } })
        put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
    }

    companion object {
        const val TYPE_MATCH = "match"
        const val TYPE_SHOOTOUT = "shootout"
        const val TYPE_SETS = "sets"
        const val TYPE_BASKETBALL = "basketball"

        fun fromJson(o: JSONObject): MatchState {
            val s = MatchState(
                id = o.optLong("id", System.currentTimeMillis()), type = o.optString("type", TYPE_MATCH),
                title = o.optString("title", "مباراة"), home = o.optString("home", "الفريق ١"), away = o.optString("away", "الفريق ٢"),
                scoreHome = o.optInt("scoreHome"), scoreAway = o.optInt("scoreAway"), elapsedMs = o.optLong("elapsedMs"),
                running = o.optBoolean("running"), lostMs = o.optLong("lostMs"), pausedAt = o.optLong("pausedAt"),
                sport = o.optString("sport", "football"), halfMinutes = o.optInt("halfMinutes", 45), autoPause = o.optBoolean("autoPause", true),
                archived = o.optBoolean("archived"), notes = o.optString("notes"), referee = o.optString("referee"), kickOrder = o.optString("kickOrder"),
                setPointsHome = o.optInt("setPointsHome"), setPointsAway = o.optInt("setPointsAway"), setsHome = o.optInt("setsHome"), setsAway = o.optInt("setsAway"),
                setsToWin = o.optInt("setsToWin", 3), pointsPerSet = o.optInt("pointsPerSet", 25), decidingPoints = o.optInt("decidingPoints", 15),
                quarter = o.optInt("quarter", 1), quarterMinutes = o.optInt("quarterMinutes", 10), shotElapsedMs = o.optLong("shotElapsedMs"),
                foulsHome = o.optInt("foulsHome"), foulsAway = o.optInt("foulsAway")
            )
            val savedAt = o.optLong("savedAt", System.currentTimeMillis())
            if (s.running) {
                s.startedAt = savedAt
                if (s.type == TYPE_BASKETBALL) s.shotStartedAt = savedAt
            }
            val kicks = o.optJSONArray("kicks") ?: JSONArray()
            for (i in 0 until kicks.length()) s.kicks.add(Kick.fromJson(kicks.getJSONObject(i)))
            val sets = o.optJSONArray("setHistory") ?: JSONArray()
            for (i in 0 until sets.length()) s.setHistory.add(SetResult.fromJson(sets.getJSONObject(i)))
            val ph = o.optJSONArray("pointHistory") ?: JSONArray()
            for (i in 0 until ph.length()) s.pointHistory.add(ph.optString(i))
            val bh = o.optJSONArray("basketHistory") ?: JSONArray()
            for (i in 0 until bh.length()) s.basketHistory.add(bh.optString(i))
            val ev = o.optJSONArray("events") ?: JSONArray()
            for (i in 0 until ev.length()) s.events.add(MatchEvent.fromJson(ev.getJSONObject(i)))
            return s
        }
    }
}
