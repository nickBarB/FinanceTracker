package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import service.ValidationException;
import model.Category;
import service.BudgetService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BudgetCommandsTest {

    private final CommandFixture fixture = new CommandFixture();
    private final BudgetService service = new BudgetService();
    private final SetBudgetCommand set = new SetBudgetCommand(service);
    private final RemoveBudgetCommand remove = new RemoveBudgetCommand(service);

    @BeforeEach
    void setUp() {
        fixture.wallet.addCategory(new Category("Еда"));
    }

    @Test
    void setBudgetCreatesBudgetAndConfirms() {
        fixture.run(set, "set-budget Еда 4000");

        assertEquals(new BigDecimal("4000.00"), fixture.wallet.getBudget("Еда").orElseThrow().limit());
        assertTrue(fixture.output().contains("установлен"));
        assertTrue(fixture.output().contains("4,000.00"));
    }

    @Test
    void setBudgetAgainReportsOldAndNewLimit() {
        fixture.run(set, "set-budget Еда 4000");
        fixture.run(set, "set-budget Еда 5000");

        assertTrue(fixture.output().contains("изменён"));
        assertTrue(fixture.output().contains("было 4,000.00, стало 5,000.00"));
    }

    @Test
    void invalidAmountIsRejectedAndNothingChanges() {
        fixture.run(set, "set-budget Еда 4000");
        assertThrows(ValidationException.class, () -> fixture.run(set, "set-budget Еда abc"));

        assertEquals(new BigDecimal("4000.00"), fixture.wallet.getBudget("Еда").orElseThrow().limit());
    }

    @Test
    void removeBudgetDeletesItAndConfirms() {
        fixture.run(set, "set-budget Еда 4000");

        fixture.run(remove, "remove-budget Еда");

        assertTrue(fixture.wallet.getBudget("Еда").isEmpty());
        assertTrue(fixture.output().contains("удалён"));
    }
}
