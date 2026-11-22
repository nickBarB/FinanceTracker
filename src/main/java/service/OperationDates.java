package service;

import java.time.Clock;
import java.time.LocalDate;

public final class OperationDates {
    private OperationDates() {
    }

    public static void validate(LocalDate date, Clock clock) {
        if (date == null) {
            throw new ValidationException("Дата не указана.");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ValidationException("Дата не может быть в будущем: " + date);
        }
    }
}
