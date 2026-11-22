package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import model.Budget;
import model.Category;
import model.Operation;
import model.OperationType;
import service.NotificationService;
import service.OperationService;
import service.StatisticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddOperationCommandTest {

    private final CommandFixture fixture = new CommandFixture();
    private final OperationService service = new OperationService(support.TestClock.CLOCK);
    private final NotificationService notifications = new NotificationService(new StatisticsService());
    private final AddOperationCommand income =
            new AddOperationCommand(OperationType.INCOME, service, notifications);
    private final AddOperationCommand expense =
            new AddOperationCommand(OperationType.EXPENSE, service, notifications);

    @BeforeEach
    void setUp() {
        fixture.wallet.addCategory(new Category("Еда"));
        fixture.wallet.addCategory(new Category("Зарплата"));
    }

    @Test
    void addExpenseStoresOperationAndShowsBalance() {
        fixture.run(expense, "add-expense Еда 300");

        Operation op = fixture.wallet.getOperations().get(0);
        assertEquals(OperationType.EXPENSE, op.type());
        assertEquals(new BigDecimal("300.00"), op.amount());
        assertEquals(support.TestClock.TODAY, op.date());
        assertTrue(fixture.output().contains("Расход добавлен"));
        assertTrue(fixture.output().contains("-300.00"));
    }

    @Test
    void addIncomeStoresDescription() {
        fixture.run(income, "add-income Зарплата 20000 \"аванс\"");

        assertEquals("аванс", fixture.wallet.getOperations().get(0).description());
        assertTrue(fixture.output().contains("Доход добавлен"));
        assertTrue(fixture.output().contains("20,000.00"));
    }

    @Test
    void dateOptionSetsOperationDate() {
        fixture.run(expense, "add-expense Еда 100 --date 2020-01-05");

        assertEquals(LocalDate.of(2020, 1, 5), fixture.wallet.getOperations().get(0).date());
    }

    @Test
    void exceededBudgetAlertIsShownAfterExpense() {
        fixture.wallet.setBudget(new Budget("Еда", new BigDecimal("1000")));
        fixture.run(income, "add-income Зарплата 5000");

        fixture.run(expense, "add-expense Еда 1500");

        assertTrue(fixture.output().contains("Превышен бюджет"));
    }
}
