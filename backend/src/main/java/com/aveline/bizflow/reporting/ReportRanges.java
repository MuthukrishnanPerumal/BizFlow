package com.aveline.bizflow.reporting;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public final class ReportRanges {

    public static final int MAX_DAYS = 3660;

    private ReportRanges() {
    }

    public static Range resolve(ReportPreset preset, LocalDate from, LocalDate to, Clock clock) {
        ReportPreset selected = preset;
        if (selected == null) {
            selected = from != null || to != null ? ReportPreset.CUSTOM : ReportPreset.THIS_MONTH;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start;
        LocalDate end;
        switch (selected) {
            case TODAY -> {
                start = today;
                end = today;
            }
            case YESTERDAY -> {
                start = today.minusDays(1);
                end = start;
            }
            case THIS_WEEK -> {
                start = today.with(DayOfWeek.MONDAY);
                end = today;
            }
            case THIS_MONTH -> {
                start = today.withDayOfMonth(1);
                end = today;
            }
            case PREVIOUS_MONTH -> {
                LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                start = first;
                end = first.with(TemporalAdjusters.lastDayOfMonth());
            }
            case THIS_YEAR -> {
                start = today.withDayOfYear(1);
                end = today;
            }
            case CUSTOM -> {
                if (from == null || to == null) {
                    throw new BusinessRuleException("DATE_RANGE", "from", "Choose both dates for a custom range");
                }
                if (from.isAfter(to)) {
                    throw new BusinessRuleException("DATE_RANGE", "from", "The start date cannot be after the end date");
                }
                if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
                    throw new BusinessRuleException("DATE_RANGE", "to", "Custom ranges are limited to 10 years");
                }
                start = from;
                end = to;
            }
            default -> throw new BusinessRuleException("DATE_RANGE", "preset", "That date preset is not supported");
        }
        return new Range(selected, start, end, grain(start, end));
    }

    public static Grain grain(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 62) {
            return Grain.DAY;
        }
        if (days <= 730) {
            return Grain.MONTH;
        }
        return Grain.YEAR;
    }

    public static List<String> buckets(LocalDate from, LocalDate to, Grain grain) {
        List<String> keys = new ArrayList<>();
        switch (grain) {
            case DAY -> {
                for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                    keys.add(day.toString());
                }
            }
            case MONTH -> {
                YearMonth cursor = YearMonth.from(from);
                YearMonth last = YearMonth.from(to);
                while (!cursor.isAfter(last)) {
                    keys.add(cursor.toString());
                    cursor = cursor.plusMonths(1);
                }
            }
            case YEAR -> {
                for (int year = from.getYear(); year <= to.getYear(); year++) {
                    keys.add(Integer.toString(year));
                }
            }
            default -> throw new BusinessRuleException("DATE_RANGE", "preset", "That date grain is not supported");
        }
        return List.copyOf(keys);
    }

    public enum Grain {
        DAY,
        MONTH,
        YEAR
    }

    public record Range(ReportPreset preset, LocalDate from, LocalDate to, Grain grain) {
    }
}
