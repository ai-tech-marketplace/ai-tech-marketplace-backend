package com.aitechmarketplace.backend.analytics.util;

import com.aitechmarketplace.backend.common.exception.BadRequestException;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

public class DateRangeParser {

    public record DateRange(OffsetDateTime start, OffsetDateTime end) {}

    public static DateRange parse(String period, String startDateStr, String endDateStr) {
        ZoneOffset zone = ZoneOffset.UTC;
        OffsetDateTime now = OffsetDateTime.now(zone);

        if (startDateStr != null || endDateStr != null) {
            if (startDateStr == null || endDateStr == null) {
                throw new BadRequestException("Both startDate and endDate must be provided", "INVALID_DATE_RANGE");
            }

            OffsetDateTime start = parseDateTime(startDateStr, true, zone);
            OffsetDateTime end = parseDateTime(endDateStr, false, zone);

            if (start.isAfter(end)) {
                throw new BadRequestException("Start date must be before or equal to end date", "INVALID_DATE_RANGE");
            }

            if (Duration.between(start, end).toDays() > 365) {
                throw new BadRequestException("Date range cannot exceed 365 days", "INVALID_DATE_RANGE");
            }

            return new DateRange(start, end);
        }

        if (period != null && !period.isBlank()) {
            return switch (period.toLowerCase().trim()) {
                case "7d" -> new DateRange(now.minusDays(7).with(LocalTime.MIN), now);
                case "30d" -> new DateRange(now.minusDays(30).with(LocalTime.MIN), now);
                case "this_month" -> {
                    LocalDate today = now.toLocalDate();
                    LocalDate firstDay = today.withDayOfMonth(1);
                    yield new DateRange(firstDay.atStartOfDay().atOffset(zone), now);
                }
                case "last_month" -> {
                    LocalDate today = now.toLocalDate();
                    LocalDate prevMonth = today.minusMonths(1);
                    LocalDate firstDay = prevMonth.withDayOfMonth(1);
                    LocalDate lastDay = prevMonth.with(TemporalAdjusters.lastDayOfMonth());
                    yield new DateRange(
                            firstDay.atStartOfDay().atOffset(zone),
                            lastDay.atTime(23, 59, 59, 999_999_999).atOffset(zone)
                    );
                }
                default -> throw new BadRequestException("Invalid period shortcut: " + period, "INVALID_PERIOD");
            };
        }

        // Default: 30 days
        return new DateRange(now.minusDays(30).with(LocalTime.MIN), now);
    }

    private static OffsetDateTime parseDateTime(String text, boolean isStart, ZoneOffset zone) {
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {}

        try {
            LocalDate date = LocalDate.parse(text);
            return isStart ? date.atStartOfDay().atOffset(zone) : date.atTime(23, 59, 59, 999_999_999).atOffset(zone);
        } catch (DateTimeParseException ignored) {}

        throw new BadRequestException("Invalid date format: " + text + ". Expected YYYY-MM-DD or ISO-8601 offset format.", "INVALID_DATE_RANGE");
    }
}
