package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GPS run recording and auto-pause (1.14.0). */
class RunTrackingTest {
    /** [n] + 1 points heading east along the equator, about 100 m and [stepS] seconds apart. */
    private fun line(n: Int, stepS: Int = 30, seg: Int = 0, startLon: Double = 0.0, startTime: Long = 0): List<GpsPoint> =
        (0..n).map { GpsPoint(0.0, startLon + it * 0.0009, startTime + it * stepS * 1000L, seg) }

    @Test
    fun distanceOnTheEarth() {
        assertEquals(111.195, distanceKm(GpsPoint(0.0, 0.0), GpsPoint(1.0, 0.0)), 0.001)
        assertEquals(0.1001, distanceKm(GpsPoint(0.0, 0.0), GpsPoint(0.0, 0.0009)), 0.0001)
        assertEquals(0.0, distanceKm(GpsPoint(48.2, 16.37), GpsPoint(48.2, 16.37)), 1e-12)
    }

    @Test
    fun badFixesAreDropped() {
        val a = GpsPoint(0.0, 0.0, 0)
        assertTrue(acceptPoint(null, a, 8f))
        assertFalse(acceptPoint(null, a, 50f))
        // Unknown accuracy is fine.
        assertTrue(acceptPoint(null, a, null))
        // 10 m in 3 s: running.
        assertTrue(acceptPoint(a, GpsPoint(0.0, 0.00009, 3_000), 5f))
        // About 1 m: standing still with jitter.
        assertFalse(acceptPoint(a, GpsPoint(0.0, 0.00001, 3_000), 5f))
        // 500 m in a second: a jump.
        assertFalse(acceptPoint(a, GpsPoint(0.0, 0.0045, 1_000), 5f))
        // The first fix after a pause starts a new segment wherever it is.
        assertTrue(acceptPoint(a, GpsPoint(0.0, 0.0045, 1_000, seg = 1), 5f))
    }

    @Test
    fun pausesArentDistance() {
        val first = line(10)
        val second = line(10, seg = 1, startLon = 0.05, startTime = 1_000_000)
        assertEquals(1.0008, routeDistanceKm(first), 0.001)
        // The 4.5 km between the segments isn't counted.
        assertEquals(2.0015, routeDistanceKm(first + second), 0.001)
        assertEquals(0.0, routeDistanceKm(emptyList()), 0.0)
    }

    @Test
    fun splitsPerKilometreAndMile() {
        // 30 steps of about 100 m in 30 s each: 3 km at about 5:00 /km.
        val run = line(30)
        val km = splits(run, 1.0)
        assertEquals(3, km.size)
        km.forEach { assertEquals(300.0, it.seconds.toDouble(), 1.0) }
        assertEquals(listOf(1, 2, 3), km.map { it.number })
        // Miles: one full mile and the rest.
        val mi = splits(run, KM_PER_MILE)
        assertEquals(2, mi.size)
        assertEquals(KM_PER_MILE, mi[0].km, 1e-9)
        assertEquals(482.0, mi[0].seconds.toDouble(), 2.0)
        assertEquals(3.0023 - KM_PER_MILE, mi[1].km, 0.001)
        // The pause between segments isn't in any split.
        val paused = line(15) + line(15, seg = 1, startLon = 0.0135, startTime = 10_000_000)
        val p = splits(paused, 1.0)
        assertEquals(3, p.size)
        p.forEach { assertEquals(300.0, it.seconds.toDouble(), 1.0) }
        assertTrue(splits(line(0), 1.0).isEmpty())
    }

    @Test
    fun autoPausePausesAfterStandingStillAndResumesWhenMoving() {
        var st = AutoPauseState()
        // Running: nothing happens.
        var step = autoPauseStep(st, autoPaused = false, speedMs = 3.0, now = 0)
        assertNull(step.pauseFrom)
        assertFalse(step.resume)
        // Stopping at a light at 10 s: not yet after 5 s...
        step = autoPauseStep(step.state, false, 0.2, 10_000)
        step = autoPauseStep(step.state, false, 0.3, 15_000)
        assertNull(step.pauseFrom)
        // ...paused after 6 s, from when the runner stopped.
        step = autoPauseStep(step.state, false, 0.1, 16_000)
        assertEquals(10_000L, step.pauseFrom)
        // A slow moment that ends in time doesn't pause.
        st = autoPauseStep(AutoPauseState(), false, 0.2, 0).state
        st = autoPauseStep(st, false, 2.5, 3_000).state
        assertNull(autoPauseStep(st, false, 0.2, 7_000).pauseFrom)
        assertNull(autoPauseStep(autoPauseStep(st, false, 0.2, 7_000).state, false, 0.2, 12_000).pauseFrom)
        // Auto-paused: one quick fix isn't enough (GPS jitter), two in a row resume.
        step = autoPauseStep(AutoPauseState(), autoPaused = true, speedMs = 2.0, now = 20_000)
        assertFalse(step.resume)
        assertFalse(autoPauseStep(step.state, true, 0.3, 21_000).resume)
        step = autoPauseStep(step.state, true, 2.0, 21_000)
        assertTrue(step.resume)
        // Walking speed between the two thresholds neither pauses nor resumes.
        assertFalse(autoPauseStep(AutoPauseState(fastFixes = 1), true, 1.0, 0).resume)
        assertNull(autoPauseStep(AutoPauseState(slowSince = 0), false, 1.0, 60_000).pauseFrom)
    }

    @Test
    fun paces() {
        assertEquals("5:00", paceText(300.0, "km"))
        assertEquals("8:03", paceText(300.0, "mi"))
        assertEquals("–", paceText(null, "km"))
        assertEquals("–", paceText(Double.POSITIVE_INFINITY, "km"))
    }

    @Test
    fun aTrackCountsOnlyMovingTime() {
        val t = ActiveTrack(movedMs = 60_000, resumedAt = 1_000_000, points = line(10, stepS = 3), distanceKm = 1.0008)
        assertFalse(t.paused)
        assertEquals(90_000, t.movingMs(1_030_000))
        assertEquals(90.0, t.paceSecPerKm(1_030_000)!!, 0.2)
        // The last 30 s of points: about 1 km in 30 s.
        assertEquals(30.0, t.currentPaceSecPerKm()!!, 0.5)
        val paused = t.copy(resumedAt = null)
        assertTrue(paused.paused)
        assertEquals(60_000, paused.movingMs(5_000_000))
        assertNull(ActiveTrack().paceSecPerKm(0))
        assertNull(ActiveTrack().currentPaceSecPerKm())
    }

    @Test
    fun routesAndTracksRoundTrip() {
        val gson = Gson()
        val points = line(3)
        val json = gson.toJson(points)
        // Short field names keep route files small.
        assertTrue(json.contains("\"a\":") && json.contains("\"o\":") && !json.contains("lat"))
        assertEquals(points, gson.fromJson<List<GpsPoint>>(json, object : TypeToken<List<GpsPoint>>() {}.type))
        val track = ActiveTrack(
            weId = "w", setId = "s", points = points, distanceKm = 0.3, seg = 1, movedMs = 5, resumedAt = 7, lastAccuracy = 4f,
            autoPaused = true,
        )
        assertEquals(track, gson.fromJson(gson.toJson(track), ActiveTrack::class.java))
        val set = SetEntry(distanceKm = 5.0, seconds = 1500, routeId = "r")
        assertEquals(set, gson.fromJson(gson.toJson(set), SetEntry::class.java))
    }
}
