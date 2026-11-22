package repository.json;

import static org.junit.jupiter.api.Assertions.*;

import libs.storage.StorageException;
import model.Category;
import model.User;
import model.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import service.OperationService;
import service.TransferService;
import service.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class JsonPersistenceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("finance.json");
    }

    private void seed(JsonStorage storage) {
        var users = new JsonUserRepository(storage);
        var wallets = new JsonWalletRepository(storage);
        storage.inTransaction(() -> {
            users.save(new User("anna", "synthetic-hash"));
            users.save(new User("boris", "synthetic-hash"));
            Wallet wallet = wallets.findByOwner("anna").orElseThrow();
            wallet.addCategory(new Category("Зарплата"));
            new OperationService(CLOCK).addIncome(wallet, "Зарплата", new BigDecimal("1000"), TODAY, "доход");
            wallets.save(wallet);
            return null;
        });
    }

    @Test
    void usersAndUnicodeOperationsSurviveRestart() {
        try (var storage = new JsonStorage(file())) {
            seed(storage);
        }
        try (var storage = new JsonStorage(file())) {
            var wallet = new JsonWalletRepository(storage).findByOwner("ANNA").orElseThrow();
            assertEquals(new BigDecimal("1000.00"), wallet.getBalance());
            assertEquals("доход", wallet.getOperations().get(0).description());
            assertEquals(TODAY, wallet.getOperations().get(0).date());
            assertTrue(new JsonUserRepository(storage).findByLogin("ANNA").isPresent());
        }
    }

    @Test
    void transferCommitsBothWalletsAndPreservesTotal() {
        try (var storage = new JsonStorage(file())) {
            seed(storage);
            var wallets = new JsonWalletRepository(storage);
            var service = new TransferService(new JsonUserRepository(storage), wallets, storage, CLOCK);
            Wallet sender = wallets.findByOwner("anna").orElseThrow();
            service.transfer(sender, "BORIS", new BigDecimal("300"), TODAY, "обед");
            assertEquals(new BigDecimal("700.00"), sender.getBalance());
        }
        try (var storage = new JsonStorage(file())) {
            var wallets = new JsonWalletRepository(storage);
            Wallet sender = wallets.findByOwner("anna").orElseThrow();
            Wallet target = wallets.findByOwner("boris").orElseThrow();
            assertEquals(new BigDecimal("700.00"), sender.getBalance());
            assertEquals(new BigDecimal("300.00"), target.getBalance());
            assertEquals("anna", target.getOperations().get(0).counterparty());
            assertEquals(new BigDecimal("1000.00"), sender.getBalance().add(target.getBalance()));
        }
    }

    @Test
    void failureAfterBothTransferWritesRollsBackEntireSnapshot() throws Exception {
        try (var storage = new JsonStorage(file())) {
            seed(storage);
            String before = Files.readString(file());
            var wallets = new JsonWalletRepository(storage);
            var service = new TransferService(new JsonUserRepository(storage), wallets, storage, CLOCK);
            assertThrows(StorageException.class, () -> storage.inTransaction(() -> {
                service.transfer(wallets.findByOwner("anna").orElseThrow(), "boris",
                    new BigDecimal("300"), TODAY, "");
                throw new StorageException("Сбой перед фиксацией");
            }));
            assertEquals(before, Files.readString(file()));
            assertEquals(new BigDecimal("1000.00"), wallets.findByOwner("anna").orElseThrow().getBalance());
            assertEquals(new BigDecimal("0.00"), wallets.findByOwner("boris").orElseThrow().getBalance());
        }
    }

    @Test
    void failedRegistrationLeavesNoUserOrWallet() {
        try (var storage = new JsonStorage(file())) {
            var users = new JsonUserRepository(storage);
            assertThrows(IllegalStateException.class, () -> storage.inTransaction(() -> {
                users.save(new User("anna", "synthetic-hash"));
                throw new IllegalStateException("отмена");
            }));
            assertTrue(users.findByLogin("anna").isEmpty());
            assertTrue(new JsonWalletRepository(storage).findByOwner("anna").isEmpty());
            assertFalse(Files.exists(file()));
        }
    }

    @Test
    void competingStorageInstancesCannotOverspendOrLoseTransfers() throws Exception {
        try (var first = new JsonStorage(file()); var second = new JsonStorage(file())) {
            seed(first);
            var firstWallets = new JsonWalletRepository(first);
            var secondWallets = new JsonWalletRepository(second);
            Wallet staleFirst = firstWallets.findByOwner("anna").orElseThrow();
            Wallet staleSecond = secondWallets.findByOwner("anna").orElseThrow();
            var firstTransfers = new TransferService(new JsonUserRepository(first), firstWallets, first, CLOCK);
            var secondTransfers = new TransferService(new JsonUserRepository(second), secondWallets, second, CLOCK);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Callable<Boolean> sendFirst = () -> send(firstTransfers, staleFirst);
                Callable<Boolean> sendSecond = () -> send(secondTransfers, staleSecond);
                var left = executor.submit(sendFirst);
                var right = executor.submit(sendSecond);
                int successes = (left.get(5, TimeUnit.SECONDS) ? 1 : 0) + (right.get(5, TimeUnit.SECONDS) ? 1 : 0);
                assertEquals(1, successes);
            }
            assertEquals(new BigDecimal("300.00"), firstWallets.findByOwner("anna").orElseThrow().getBalance());
            assertEquals(new BigDecimal("700.00"), firstWallets.findByOwner("boris").orElseThrow().getBalance());
        }
    }

    private boolean send(TransferService transfers, Wallet sender) {
        try {
            transfers.transfer(sender, "boris", new BigDecimal("700"), TODAY, "");
            return true;
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("Недостаточно средств"));
            return false;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{bad", "{\"version\":2,\"users\":[],\"wallets\":[]}",
        "{\"version\":1,\"users\":[],\"wallets\":null}",
        "{\"version\":1,\"users\":[{\"login\":\"anna\",\"passwordHash\":\"h\"}],\"wallets\":[]}"})
    void malformedOrIncompleteSnapshotIsNotOverwritten(String json) throws Exception {
        Files.writeString(file(), json);
        assertThrows(StorageException.class, () -> new JsonStorage(file()));
        assertEquals(json, Files.readString(file()));
    }

    @Test
    void unknownWalletOwnerCannotBePersisted() {
        try (var storage = new JsonStorage(file())) {
            assertThrows(StorageException.class, () -> new JsonWalletRepository(storage).save(new Wallet("nobody")));
            assertFalse(Files.exists(file()));
        }
    }

    @Test
    void duplicateLoginsInSnapshotAreRejectedWithoutOverwriting() throws Exception {
        try (var storage = new JsonStorage(file())) {
            seed(storage);
        }
        var json = com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        var users = json.getAsJsonArray("users");
        users.add(users.get(0).deepCopy());
        String broken = libs.json.JsonCodec.create().toJson(json);
        Files.writeString(file(), broken);
        assertThrows(StorageException.class, () -> new JsonStorage(file()));
        assertEquals(broken, Files.readString(file()));
    }

    @Test
    void corruptedWalletCannotSilentlyDropItsCollections() throws Exception {
        try (var storage = new JsonStorage(file())) {
            seed(storage);
        }
        var gson = libs.json.JsonCodec.create();
        var json = com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        json.getAsJsonArray("wallets").get(1).getAsJsonObject().remove("operations");
        String broken = gson.toJson(json);
        Files.writeString(file(), broken);
        assertThrows(StorageException.class, () -> new JsonStorage(file()));
        assertEquals(broken, Files.readString(file()));
    }
}
