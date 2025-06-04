package com.frottage

import android.content.Context
import coil.request.ImageRequest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

interface Schedule {
    fun nextUpdateTime(now: ZonedDateTime): ZonedDateTime

    fun prevUpdateTime(now: ZonedDateTime): ZonedDateTime

    fun zonedDateTimeToStringKey(
        dateTime: ZonedDateTime,
        pattern: String = "yyyy-MM-dd_HH-mm-ss",
    ): String = dateTime.format(DateTimeFormatter.ofPattern(pattern))

    fun getActivePeriodTimestampKey(now: ZonedDateTime): String = zonedDateTimeToStringKey(prevUpdateTime(now))

    fun imageRequest(
        url: String,
        context: Context,
        timestampKey: String,
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(url)
            .diskCacheKey(timestampKey)
            .memoryCacheKey(timestampKey)
            .build()
}

data class UtcHoursSchedule(
    val hours: List<Int>,
) : Schedule {
    override fun nextUpdateTime(currentTime: ZonedDateTime): ZonedDateTime {
        val nextUpdateHour = hours.firstOrNull { it > currentTime.hour } ?: hours.first()
        val nextUpdateTime = currentTime.withHour(nextUpdateHour).truncatedTo(ChronoUnit.HOURS)
        return if (nextUpdateHour > currentTime.hour) nextUpdateTime else nextUpdateTime.plusDays(1)
    }

    override fun prevUpdateTime(currentTime: ZonedDateTime): ZonedDateTime {
        // Find the latest scheduled hour on the current day that is less than or equal to the current hour
        val currentDayPrevHour = hours.lastOrNull { it <= currentTime.hour }

        return if (currentDayPrevHour != null) {
            // A scheduled hour for the current slot was found on the current day
            currentTime.withHour(currentDayPrevHour).truncatedTo(ChronoUnit.HOURS)
        } else {
            // Current time is before the first scheduled hour of today.
            // So, the previous update time was the last scheduled hour of the previous day.
            currentTime.minusDays(1).withHour(hours.last()).truncatedTo(ChronoUnit.HOURS)
        }
    }
}

object EveryMinuteSchedule : Schedule {
    override fun nextUpdateTime(currentTime: ZonedDateTime): ZonedDateTime = currentTime.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)

    override fun prevUpdateTime(currentTime: ZonedDateTime): ZonedDateTime = currentTime.truncatedTo(ChronoUnit.MINUTES)
}

data class EveryXSecondsSchedule(
    val seconds: Int,
) : Schedule {
    init {
        // Fail fast if invalid seconds value is provided
        require(seconds > 0) { "seconds must be positive, got $seconds" }
    }

    override fun nextUpdateTime(currentTime: ZonedDateTime): ZonedDateTime {
        // Round up to the next interval
        val currentSecond = currentTime.second
        val nextIntervalSeconds = ((currentSecond / seconds) + 1) * seconds
        return currentTime
            .withSecond(0)
            .plusSeconds(nextIntervalSeconds.toLong())
            .truncatedTo(ChronoUnit.SECONDS)
    }

    override fun prevUpdateTime(currentTime: ZonedDateTime): ZonedDateTime {
        // Round down to the previous interval
        val currentSecond = currentTime.second
        val prevIntervalSeconds = (currentSecond / seconds) * seconds
        return currentTime
            .withSecond(prevIntervalSeconds)
            .truncatedTo(ChronoUnit.SECONDS)
    }
}
