package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import model.Operation;
import model.OperationType;
import model.Wallet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticsSummaryTest {

    private StatisticsService service;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new StatisticsService();
        wallet = support.FinancialFixtures.wallet("anna");
    }

    private static LocalDate d(int month, int day) {
        return LocalDate.of(2020, month, day);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void summaryGroupsIncomeAndExpenses() {
        Summary summary = service.summarize(wallet, OperationFilter.all());

        assertEquals(money("63000.00"), summary.totalIncome());
        assertEquals(money("8300.00"), summary.totalExpense());
        assertEquals(Map.of("Зарплата", money("60000.00"), "Бонус", money("3000.00")),
                summary.incomeByCategory());
        assertEquals(Map.of("Еда", money("800.00"), "Развлечения", money("3000.00"),
                        "Коммунальные услуги", money("3000.00"), "Такси", money("1500.00")),
                summary.expenseByCategory());
        assertFalse(summary.empty());
    }

    @Test
    void budgetStatusesShowSpentAndRemaining() {
        List<BudgetStatus> budgets = service.summarize(wallet, OperationFilter.all()).budgets();

        assertEquals(List.of("Еда", "Развлечения", "Коммунальные услуги"),
                budgets.stream().map(BudgetStatus::category).toList());
        assertEquals(money("800.00"), budgets.get(0).spent());
        assertEquals(money("3200.00"), budgets.get(0).remaining());
        assertEquals(money("0.00"), budgets.get(1).remaining());
        assertEquals(money("-500.00"), budgets.get(2).remaining());
    }

    @Test
    void categoriesKeepCreationOrder() {
        Summary summary = service.summarize(wallet, OperationFilter.all());

        assertEquals(List.of("Еда", "Развлечения", "Коммунальные услуги", "Такси"),
                new ArrayList<>(summary.expenseByCategory().keySet()));
    }

    @Test
    void selectedCategoriesLimitListsAndBudgets() {
        OperationFilter filter = new OperationFilter(List.of("еда"), null, null);

        Summary summary = service.summarize(wallet, filter);

        assertEquals(Map.of("Еда", money("800.00")), summary.expenseByCategory());
        assertTrue(summary.incomeByCategory().isEmpty());
        assertEquals(1, summary.budgets().size());
        assertEquals("Еда", summary.budgets().get(0).category());
    }

    @Test
    void periodAffectsBudgetSpending() {
        OperationFilter filter = new OperationFilter(null, d(2, 1), d(2, 28));

        List<BudgetStatus> budgets = service.summarize(wallet, filter).budgets();

        assertEquals(money("3500.00"), budgets.get(0).remaining());
        assertEquals(money("3000.00"), budgets.get(1).remaining());
        assertEquals(money("-500.00"), budgets.get(2).remaining());
    }

    @Test
    void emptySelectionIsMarkedAndKeepsBudgets() {
        OperationFilter filter = new OperationFilter(null, LocalDate.of(2021, 1, 1), null);

        Summary summary = service.summarize(wallet, filter);

        assertTrue(summary.empty());
        assertEquals(money("0.00"), summary.totalExpense());
        assertTrue(summary.expenseByCategory().isEmpty());
        assertEquals(3, summary.budgets().size());
        assertEquals(money("4000.00"), summary.budgets().get(0).remaining());
    }

    @Test
    void operationCategoryCaseDoesNotSplitGroups() {
        wallet.addOperation(new Operation(OperationType.EXPENSE, "еда", new BigDecimal("100"),
            d(3, 1), null, null));

        Summary summary = service.summarize(wallet, OperationFilter.all());

        assertEquals(money("900.00"), summary.expenseByCategory().get("Еда"));
        assertFalse(summary.expenseByCategory().containsKey("еда"));
    }

    @Test
    void unknownCategoryInSummaryFails() {
        OperationFilter filter = new OperationFilter(List.of("Кино"), null, null);

        assertThrows(ValidationException.class, () -> service.summarize(wallet, filter));
    }
}
