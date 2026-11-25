package dev.titis.incentive.ledger.service;

import dev.titis.incentive.ledger.exception.BusinessException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class Periods {
    private Periods() { }
    public static YearMonth parse(String text) {
        try {
            var result = YearMonth.parse(text);
            if (result.getYear() < 1 || result.getYear() > 9999 || !result.toString().equals(text)) throw new DateTimeParseException("Invalid period", text, 0);
            return result;
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(400, "INVALID_PERIOD", "Period must be a valid calendar month YYYY-MM");
        }
    }
}