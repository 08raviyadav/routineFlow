package com.thor.routineflow.engine

import java.util.Calendar
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Approximate sunrise / sunset from latitude, longitude and date.
 *
 * Small NOAA-style solar-position calculation — no network, no weather API.
 * Accuracy is within a few minutes for most latitudes, which is why the
 * triggers are labelled "(approx.)". Near the poles (midnight sun / polar
 * night) it returns null and the trigger simply does not fire that day.
 */
object SunCalculator {

    data class SunTimes(val sunriseMillis: Long, val sunsetMillis: Long)

    fun sunTimes(latitude: Double, longitude: Double, dayMillis: Long): SunTimes? {
        val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        // Longitude hour: solar noon offset from UTC.
        val lngHour = longitude / 15.0
        val sunrise = eventTime(latitude, lngHour, dayOfYear, isSunrise = true, cal)
        val sunset = eventTime(latitude, lngHour, dayOfYear, isSunrise = false, cal)
        if (sunrise == null || sunset == null) return null
        return SunTimes(sunrise, sunset)
    }

    private fun eventTime(
        latitude: Double,
        lngHour: Double,
        dayOfYear: Int,
        isSunrise: Boolean,
        dayCal: Calendar
    ): Long? {
        // Approximate time.
        val t = dayOfYear + ((if (isSunrise) 6.0 else 18.0) - lngHour) / 24.0
        // Sun's mean anomaly.
        val m = (0.9856 * t) - 3.289
        // Sun's true longitude.
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = ((l % 360.0) + 360.0) % 360.0
        // Sun's right ascension.
        var ra = Math.toDegrees(kotlin.math.atan(0.91764 * tan(Math.toRadians(l))))
        ra = ((ra % 360.0) + 360.0) % 360.0
        // Right ascension quadrant adjustment.
        val lQuadrant = (Math.floor(l / 90.0) * 90.0)
        val raQuadrant = (Math.floor(ra / 90.0) * 90.0)
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0
        // Sun's declination.
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        // Sun's local hour angle (zenith 90.833° = official sunrise/sunset).
        val cosH = (cos(Math.toRadians(90.833)) - (sinDec * sin(Math.toRadians(latitude)))) /
            (cosDec * cos(Math.toRadians(latitude)))
        if (cosH > 1.0 || cosH < -1.0) return null // Sun never rises / never sets.
        val h = if (isSunrise) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        val hHours = h / 15.0
        // Local mean time of rising/setting.
        val localMeanTime = hHours + ra - (0.06571 * t) - 6.622
        // Convert to UTC, then to device-local wall time.
        var utc = localMeanTime - lngHour
        utc = ((utc % 24.0) + 24.0) % 24.0
        val zoneOffsetHours =
            (dayCal.timeZone.getOffset(dayCal.timeInMillis) / 3_600_000.0)
        var local = utc + zoneOffsetHours
        local = ((local % 24.0) + 24.0) % 24.0
        val out = Calendar.getInstance().apply {
            timeInMillis = dayCal.timeInMillis
            set(Calendar.HOUR_OF_DAY, local.toInt())
            set(Calendar.MINUTE, ((local % 1.0) * 60).toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return out.timeInMillis
    }
}
