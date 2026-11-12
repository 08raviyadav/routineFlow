package com.thor.routineflow

import com.thor.routineflow.engine.SunCalculator
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [SunCalculator]. The algorithm is approximate (within a few
 * minutes), so all bounds are deliberately broad.
 *
 * SunCalculator converts to the device's default time zone, so each test sets
 * the default time zone to the location under test and restores it afterwards.
 */
class SunCalculatorTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun saveTimeZone() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    /** Noon on the given date in the given time zone (avoids midnight edge cases). */
    private fun noonMillis(year: Int, month: Int, day: Int, zoneId: String): Long {
        val zone = TimeZone.getTimeZone(zoneId)
        TimeZone.setDefault(zone)
        return Calendar.getInstance(zone).apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun minutesOfDay(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    @Test
    fun kathmandu_november_sunriseAndSunsetInExpectedRange() {
        // Kathmandu, 2026-11-12: sunrise ~06:25, sunset ~17:08 local (NPT).
        val day = noonMillis(2026, Calendar.NOVEMBER, 12, "Asia/Kathmandu")
        val times = SunCalculator.sunTimes(27.7172, 85.3240, day)
        assertNotNull("Sun times should exist for Kathmandu", times)
        times ?: return
        assertTrue(
            "Sunrise should be between 05:30 and 07:00 local, was ${minutesOfDay(times.sunriseMillis)} min",
            minutesOfDay(times.sunriseMillis) in (5 * 60 + 30)..(7 * 60)
        )
        assertTrue(
            "Sunset should be between 16:30 and 18:30 local, was ${minutesOfDay(times.sunsetMillis)} min",
            minutesOfDay(times.sunsetMillis) in (16 * 60 + 30)..(18 * 60 + 30)
        )
    }

    @Test
    fun kathmandu_sunriseIsBeforeSunset() {
        val day = noonMillis(2026, Calendar.NOVEMBER, 12, "Asia/Kathmandu")
        val times = SunCalculator.sunTimes(27.7172, 85.3240, day)
        assertNotNull(times)
        times ?: return
        assertTrue(times.sunriseMillis < times.sunsetMillis)
    }

    @Test
    fun sydney_november_southernHemisphereSanity() {
        // Sydney in November (late spring): sunrise well before 07:00, sunset
        // well after 18:00 local (AEDT).
        val day = noonMillis(2026, Calendar.NOVEMBER, 12, "Australia/Sydney")
        val times = SunCalculator.sunTimes(-33.87, 151.21, day)
        assertNotNull("Sun times should exist for Sydney", times)
        times ?: return
        assertTrue(
            "Sydney sunrise should be before 07:00 local",
            minutesOfDay(times.sunriseMillis) < 7 * 60
        )
        assertTrue(
            "Sydney sunset should be after 18:00 local",
            minutesOfDay(times.sunsetMillis) > 18 * 60
        )
        assertTrue(times.sunriseMillis < times.sunsetMillis)
    }

    @Test
    fun returnsResultForNormalLatitudes() {
        val day = noonMillis(2026, Calendar.JUNE, 21, "UTC")
        assertNotNull(SunCalculator.sunTimes(51.5, -0.12, day)) // London
        assertNotNull(SunCalculator.sunTimes(40.71, -74.0, day)) // New York
        assertNotNull(SunCalculator.sunTimes(0.0, 0.0, day)) // Equator
    }
}
