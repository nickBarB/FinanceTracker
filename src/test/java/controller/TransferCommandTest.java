package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import service.ValidationException;
import model.Category;
import model.Operation;
import model.OperationType;
import model.User;
import service.NotificationService;
import service.StatisticsService;
import service.TransferService;
import repository.memory.InMemoryUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

    private final CommandFixture fixture = new CommandFixture();
    private final RecordingWalletRepository wallets = new RecordingWalletRepository();
    private TransferCommand command;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        users.save(new User("anna", "h"));
        users.save(new User("boris", "h"));
        command = new TransferCommand(new TransferService(users, wallets, new support.DirectTransactions(), java.time.Clock.systemDefaultZone()),
                new NotificationService(new StatisticsService()));

        fixture.wallet.addCategory(new Category("Зарплата"));
        fixture.wallet.addOperation(new Operation(OperationType.INCOME, "Зарплата",
                new BigDecimal("1000"), LocalDate.of(2020, 1, 1), null, null));
    }

    @Test
    void transferMovesMoneyAndConfirms() {
        fixture.run(command, "transfer boris 300 \"за обед\"");

        assertEquals(new BigDecimal("700.00"), fixture.wallet.getBalance());
        assertEquals(new BigDecimal("300.00"), wallets.stored.get("boris").getBalance());
        assertTrue(fixture.output().contains("Переведено 300.00 пользователю boris"));
        assertTrue(fixture.output().contains("700.00"));
    }

    @Test
    void recipientLoginIsCaseInsensitive() {
        fixture.run(command, "transfer BORIS 100");

        assertTrue(wallets.stored.containsKey("boris"));
    }

    @Test
    void insufficientFundsAreRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(command, "transfer boris 5000"));

        assertTrue(e.getMessage().contains("Недостаточно средств"));
        assertEquals(new BigDecimal("1000.00"), fixture.wallet.getBalance());
    }

    @Test
    void unknownRecipientIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(command, "transfer nobody 100"));

        assertTrue(e.getMessage().contains("не найден"));
    }

    @Test
    void transferToSelfIsRejected() {
        assertThrows(ValidationException.class, () -> fixture.run(command, "transfer anna 100"));
    }

    @Test
    void invalidAmountIsRejected() {
        assertThrows(ValidationException.class, () -> fixture.run(command, "transfer boris abc"));
    }

    @Test
    void missingAmountShowsUsage() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(command, "transfer boris"));

        assertTrue(e.getMessage().contains("transfer <логин> <сумма>"));
    }

    @Test
    void zeroBalanceAlertIsShownAfterTransferOfAllMoney() {
        fixture.run(command, "transfer boris 1000");

        assertTrue(fixture.output().contains("Баланс равен нулю"));
    }

    @Test
    void commandIsMarkedForSaving() {
        assertTrue(command.changesData());
    }
}
