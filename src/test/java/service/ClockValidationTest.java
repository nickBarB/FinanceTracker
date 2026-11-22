package service;

import static org.junit.jupiter.api.Assertions.*;

import model.Category;
import model.Operation;
import model.OperationType;
import model.Wallet;
import model.WalletContent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

class ClockValidationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2020-01-05T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void usesInjectedClockForTodayAndFutureValidation() {
        Wallet wallet = new Wallet("anna");
        wallet.addCategory(new Category("Еда"));
        var service = new OperationService(CLOCK);
        assertEquals(LocalDate.of(2020, 1, 5), service.today());
        service.addExpense(wallet, "Еда", BigDecimal.TEN, service.today(), "");
        assertThrows(ValidationException.class,
            () -> service.addExpense(wallet, "Еда", BigDecimal.TEN, service.today().plusDays(1), ""));
        assertEquals(1, wallet.getOperations().size());
    }

    @Test
    void invalidImportDoesNotEvenCreateMissingCategories() {
        Wallet wallet = new Wallet("anna");
        var operation = new Operation(OperationType.EXPENSE, "Еда", BigDecimal.TEN,
            LocalDate.of(2020, 1, 6), "", null);
        var content = new WalletContent(List.of(new Category("Еда")), List.of(), List.of(operation));
        assertThrows(ValidationException.class, () -> new ImportService(CLOCK).merge(wallet, content));
        assertTrue(wallet.getCategories().isEmpty());
        assertTrue(wallet.getOperations().isEmpty());
    }
}
