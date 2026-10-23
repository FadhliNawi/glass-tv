package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class ScreenTimeTest {
    @Test
    public void formatsMinutesAndHours() {
        assertEquals("0 minit", ScreenTime.format(0));
        assertEquals("45 minit", ScreenTime.format(45 * ScreenTime.MINUTE_MS));
        assertEquals("1 jam", ScreenTime.format(60 * ScreenTime.MINUTE_MS));
        assertEquals("2 jam 5 minit", ScreenTime.format(125 * ScreenTime.MINUTE_MS + 30_000));
        assertEquals("0 minit", ScreenTime.format(-5));
    }

    @Test
    public void readsDayNamesInEnglishAndMalay() {
        assertEquals(Calendar.FRIDAY, ScreenTime.dayIndex("fri"));
        assertEquals(Calendar.FRIDAY, ScreenTime.dayIndex("Jumaat"));
        assertEquals(Calendar.SATURDAY, ScreenTime.dayIndex(" sabtu "));
        assertEquals(0, ScreenTime.dayIndex("someday"));
    }

    @Test
    public void weekendDaysGetTheirOwnLimit() {
        boolean[] fs = new boolean[8];
        fs[Calendar.FRIDAY] = true;
        fs[Calendar.SATURDAY] = true;
        ScreenTime.Settings s = new ScreenTime.Settings(60, 180, fs, "2580", 5);
        assertTrue(s.enabled());
        assertEquals(60, s.minutesFor(Calendar.MONDAY));
        assertEquals(60, s.minutesFor(Calendar.SUNDAY));
        assertEquals(180, s.minutesFor(Calendar.FRIDAY));
        assertEquals(180, s.minutesFor(Calendar.SATURDAY));
    }

    @Test
    public void offMeansNoLimit() {
        assertFalse(ScreenTime.OFF.enabled());
        assertEquals(0, ScreenTime.OFF.minutesFor(Calendar.MONDAY));
    }
}
