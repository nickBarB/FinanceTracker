package service;

import model.Operation;
import model.OperationType;
import model.Wallet;
import java.math.BigDecimal;
import java.time.LocalDate;

public class OperationService {

    private final java.time.Clock clock;

    public OperationService() {
        this(java.time.Clock.systemDefaultZone());
    }

    public OperationService(java.time.Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Operation addIncome(
            Wallet wallet, String category, BigDecimal amount, LocalDate date, String description) {
        return add(wallet, OperationType.INCOME, category, amount, date, description);
    }

    public Operation addExpense(
            Wallet wallet, String category, BigDecimal amount, LocalDate date, String description) {
        return add(wallet, OperationType.EXPENSE, category, amount, date, description);
    }

    private Operation add(
            Wallet wallet,
            OperationType type,
            String category,
            BigDecimal amount,
            LocalDate date,
            String description) {
        OperationDates.validate(date, clock);
        Operation operation = new Operation(type, category, amount, date, description, null);
        wallet.addOperation(operation);
        return operation;
    }
}
