package io.coreplatform.billing.application.support;

import io.coreplatform.billing.application.exception.BillingBusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BillingValues {

    private BillingValues() {
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null) {
                result.put(String.valueOf(pairs[i]), value);
            }
        }
        return result;
    }

    public static String requiredString(Map<String, Object> values, String key) {
        String value = string(values.get(key));
        if (value.isBlank()) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_FIELD_REQUIRED", key + " 不能为空");
        }
        return value;
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static String string(Map<String, Object> values, String key, String defaultValue) {
        String value = string(values.get(key));
        return value.isBlank() ? defaultValue : value;
    }

    public static BigDecimal decimal(Object value) {
        if (value == null || string(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    public static BigDecimal positive(Map<String, Object> values, String key) {
        BigDecimal value = decimal(values.get(key));
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_AMOUNT_INVALID", key + " 必须大于 0");
        }
        return value;
    }

    public static Long longValue(Object value) {
        if (value == null || string(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public static int intValue(Object value, int defaultValue) {
        if (value == null || string(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public static BigDecimal rowDecimal(Map<String, Object> row, String field) {
        return decimal(row.get(field));
    }

    public static Long rowLong(Map<String, Object> row, String field) {
        return longValue(row.get(field));
    }

    public static String rowString(Map<String, Object> row, String field) {
        return string(row.get(field));
    }

    public static String number(String prefix) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return prefix + time + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public static String today() {
        return LocalDate.now().toString();
    }

    public static String month() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    public static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(number.longValue()), ZoneId.systemDefault());
        }
        String text = string(value);
        if (text.matches("\\d{11,}")) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(text)), ZoneId.systemDefault());
        }
        return LocalDateTime.parse(text.replace(" ", "T"));
    }
}
