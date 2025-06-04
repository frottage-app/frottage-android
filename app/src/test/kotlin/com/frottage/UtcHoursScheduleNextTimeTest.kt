package com.frottage

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class UtcHoursScheduleNextTimeTest {
    private val utcZone = ZoneId.of("UTC")

    // Standard schedule for most tests
    private val standardScheduleHours = listOf(2, 8, 14, 20)
    private val standardSchedule = UtcHoursSchedule(standardScheduleHours)

    @Test
    fun testBeforeFirstUpdateHour() {
        // Purpose: Verify that if the current time is before the first scheduled hour of the day,
        // the next update time is correctly identified as the first scheduled hour of the current day.
        // Uses standardSchedule
        val currentTime = ZonedDateTime.of(2023, 1, 1, 1, 0, 0, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 1, 2, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, standardSchedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testBetweenUpdateHours() {
        // Purpose: Verify that if the current time is between two scheduled hours,
        // the next update time is correctly identified as the next upcoming scheduled hour on the current day.
        // Uses standardSchedule
        val currentTime = ZonedDateTime.of(2023, 1, 1, 10, 30, 0, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 1, 14, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, standardSchedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testAfterLastUpdateHour() {
        // Purpose: Verify that if the current time is after the last scheduled hour of the day,
        // the next update time is correctly identified as the first scheduled hour of the *next* day.
        // Uses standardSchedule
        val currentTime = ZonedDateTime.of(2023, 1, 1, 22, 0, 0, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 2, 2, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, standardSchedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testExactlyAtUpdateHour() {
        // Purpose: Verify that if the current time is exactly at a scheduled hour,
        // the next update time is correctly identified as the *next* scheduled hour on the current day.
        // (Uses a custom schedule where 00:00 is a slot, next is 12:00)
        val schedule = UtcHoursSchedule(listOf(0, 12)) // Custom schedule for this test
        val currentTime = ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 1, 12, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, schedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testOneSecondBeforeUpdateHour() {
        // Purpose: Verify that if the current time is just moments before a scheduled hour,
        // the next update time is correctly identified as that immediately upcoming scheduled hour.
        val schedule = UtcHoursSchedule(listOf(0, 12)) // Custom schedule for this test
        val currentTime = ZonedDateTime.of(2023, 1, 1, 11, 59, 59, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 1, 12, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, schedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testSingleUpdateHour_timeBefore() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is before it,
        // the next update time is correctly identified as that single scheduled hour on the current day.
        val schedule = UtcHoursSchedule(listOf(15)) // Custom schedule for this test
        val currentTime = ZonedDateTime.of(2023, 1, 1, 14, 0, 0, 0, utcZone)
        val expectedNextTime = ZonedDateTime.of(2023, 1, 1, 15, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, schedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testSingleUpdateHour_timeAt() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is exactly at that hour,
        // the next update time is correctly identified as the same hour on the *next* day.
        val schedule = UtcHoursSchedule(listOf(15)) // Custom schedule for this test
        val currentTime = ZonedDateTime.of(2023, 1, 1, 15, 0, 0, 0, utcZone)
        // If current time is exactly at the only scheduled hour, next update is next day at that hour.
        val expectedNextTime = ZonedDateTime.of(2023, 1, 2, 15, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, schedule.nextUpdateTime(currentTime))
    }

    @Test
    fun testSingleUpdateHour_timeAfter() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is after that hour,
        // the next update time is correctly identified as the same hour on the *next* day.
        val schedule = UtcHoursSchedule(listOf(15)) // Custom schedule for this test
        val currentTime = ZonedDateTime.of(2023, 1, 1, 16, 0, 0, 0, utcZone)
        // If current time is after the only scheduled hour, next update is next day at that hour.
        val expectedNextTime = ZonedDateTime.of(2023, 1, 2, 15, 0, 0, 0, utcZone)
        assertEquals(expectedNextTime, schedule.nextUpdateTime(currentTime))
    }
}
