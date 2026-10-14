package com.aveline.bizflow.reporting;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportRangesTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesEveryPresetFromTheSameClock() {
        assertEquals(LocalDate.of(2026, 10, 3), resolve(ReportPreset.TODAY).from());
        assertEquals(LocalDate.of(2026, 10, 2), resolve(ReportPreset.YESTERDAY).to());
        ReportRanges.Range week = resolve(ReportPreset.THIS_WEEK);
        assertEquals(LocalDate.of(2026, 9, 28), week.from());
        assertEquals(LocalDate.of(2026, 10, 3), week.to());
        assertEquals(LocalDate.of(2026, 10, 1), resolve(ReportPreset.THIS_MONTH).from());
        ReportRanges.Range previous = resolve(ReportPreset.PREVIOUS_MONTH);
        assertEquals(LocalDate.of(2026, 9, 1), previous.from());
        assertEquals(LocalDate.of(2026, 9, 30), previous.to());
        assertEquals(LocalDate.of(2026, 1, 1), resolve(ReportPreset.THIS_YEAR).from());
        assertEquals(ReportPreset.THIS_MONTH, ReportRanges.resolve(null, null, null, clock).preset());
    }

    @Test
    void choosesAGrainThatStaysBoundedAndRejectsAnInvertedCustomRange() {
        assertEquals(ReportRanges.Grain.DAY, ReportRanges.grain(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertEquals(ReportRanges.Grain.MONTH, ReportRanges.grain(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));
        assertEquals(ReportRanges.Grain.YEAR, ReportRanges.grain(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(31, ReportRanges.buckets(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ReportRanges.Grain.DAY).size());

        BusinessRuleException inverted = assertThrows(
                BusinessRuleException.class,
                () -> ReportRanges.resolve(ReportPreset.CUSTOM, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1), clock));
        assertEquals("DATE_RANGE", inverted.getCode());
        assertThrows(
                BusinessRuleException.class,
                () -> ReportRanges.resolve(ReportPreset.CUSTOM, null, LocalDate.of(2026, 10, 1), clock));
    }

    private ReportRanges.Range resolve(ReportPreset preset) {
        return ReportRanges.resolve(preset, null, null, clock);
    }
}
