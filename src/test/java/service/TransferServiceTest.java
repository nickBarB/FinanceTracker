package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import libs.storage.StorageException;
import model.Budget;
import model.Category;
import model.User;
import model.Wallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import repository.WalletRepository;
import repository.json.JsonStorage;
import repository.json.JsonUserRepository;
import repository.json.JsonWalletRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

class TransferServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2020-01-05T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @TempDir
    Path directory;
    private Path file;
    private JsonStorage storage;
    private JsonUserRepository users;
    private JsonWalletRepository wallets;
    private TransferService transfers;
    private Wallet sender;

    @BeforeEach
    void setUp() {
        file = directory.resolve("finance.json");
        storage = new JsonStorage(file);
        users = new JsonUserRepository(storage);
        wallets = new JsonWalletRepository(storage);
        users.save(new User("anna", "synthetic-hash"));
        users.save(new User("boris", "synthetic-hash"));
        sender = wallets.findByOwner("anna").orElseThrow();
        sender.addCategory(new Category("Зарплата"));
        new OperationService(CLOCK).addIncome(sender, "Зарплата", new BigDecimal("1000"), TODAY, "");
        wallets.save(sender);
        transfers = new TransferService(users, wallets, storage, CLOCK);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void existingRecipientKeepsHistoryCategoriesAndBudget() {
        Wallet recipient = wallets.findByOwner("boris").orElseThrow();
        recipient.addCategory(new Category("Еда"));
        recipient.setBudget(new Budget("Еда", new BigDecimal("500")));
        new OperationService(CLOCK).addExpense(recipient, "Еда", new BigDecimal("50"), TODAY, "обед");
        wallets.save(recipient);

        transfers.transfer(sender, "BORIS", new BigDecimal("300"), TODAY, "возврат");

        Wallet restored = wallets.findByOwner("boris").orElseThrow();
        assertEquals(recipient.getOperations().get(0), restored.getOperations().get(0));
        assertTrue(restored.hasCategory("Еда"));
        assertEquals(recipient.getBudget("Еда"), restored.getBudget("Еда"));
        assertEquals(new BigDecimal("250.00"), restored.getBalance());
        assertEquals("anna", restored.getOperations().get(1).counterparty());
        assertEquals("boris", sender.getOperations().get(1).counterparty());
        assertEquals("возврат", restored.getOperations().get(1).description());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "1001"})
    void invalidAmountLeavesFileAndCallerUnchanged(String amount) throws Exception {
        String before = Files.readString(file);
        var operations = sender.getOperations();
        assertThrows(ValidationException.class, () -> transfers.transfer(sender, "boris",
            amount == null ? null : new BigDecimal(amount), TODAY, ""));
        assertEquals(before, Files.readString(file));
        assertEquals(operations, sender.getOperations());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "nobody", "ANNA"})
    void invalidRecipientLeavesBothWalletsUnchanged(String recipient) throws Exception {
        String before = Files.readString(file);
        assertThrows(ValidationException.class,
            () -> transfers.transfer(sender, recipient, BigDecimal.TEN, TODAY, ""));
        assertEquals(before, Files.readString(file));
        assertEquals(new BigDecimal("1000.00"), sender.getBalance());
    }

    @Test
    void futureAndMissingDatesUseInjectedClockAndChangeNothing() throws Exception {
        String before = Files.readString(file);
        assertEquals(TODAY, transfers.today());
        assertThrows(ValidationException.class,
            () -> transfers.transfer(sender, "boris", BigDecimal.TEN, TODAY.plusDays(1), ""));
        assertThrows(ValidationException.class,
            () -> transfers.transfer(sender, "boris", BigDecimal.TEN, null, ""));
        assertEquals(before, Files.readString(file));
        assertEquals(new BigDecimal("1000.00"), sender.getBalance());
    }

    @ParameterizedTest
    @ValueSource(strings = {"anna", "boris"})
    void failureSavingEitherSideRollsBackBothAndKeepsCaller(String failingOwner) throws Exception {
        String before = Files.readString(file);
        WalletRepository failing = new WalletRepository() {
            @Override
            public Optional<Wallet> findByOwner(String login) {
                return wallets.findByOwner(login);
            }

            @Override
            public void save(Wallet wallet) {
                wallets.save(wallet);
                if (wallet.getOwner().equals(failingOwner)) {
                    throw new StorageException("Искусственный сбой записи");
                }
            }
        };
        var service = new TransferService(users, failing, storage, CLOCK);
        assertThrows(StorageException.class,
            () -> service.transfer(sender, "boris", BigDecimal.TEN, TODAY, ""));
        assertEquals(before, Files.readString(file));
        assertEquals(new BigDecimal("1000.00"), sender.getBalance());
        assertTrue(wallets.findByOwner("boris").orElseThrow().getOperations().isEmpty());
        transfers.transfer(sender, "boris", BigDecimal.TEN, TODAY, "повтор");
        assertEquals(new BigDecimal("990.00"), sender.getBalance());
    }
}
