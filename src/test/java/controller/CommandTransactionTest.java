package controller;

import static org.junit.jupiter.api.Assertions.*;

import libs.storage.StorageException;
import libs.storage.TransactionManager;
import model.Category;
import model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.json.JsonStorage;
import repository.json.JsonUserRepository;
import repository.json.JsonWalletRepository;
import service.CategoryService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

class CommandTransactionTest {
    @TempDir
    Path directory;

    @Test
    void failedCommitRestoresSessionAndSuppressesSuccessMessage() throws Exception {
        Path file = directory.resolve("finance.json");
        try (var storage = new JsonStorage(file)) {
            new JsonUserRepository(storage).save(new User("anna", "synthetic-hash"));
            var wallets = new JsonWalletRepository(storage);
            Session session = new Session();
            session.login(new User("anna", "synthetic-hash"), wallets.findByOwner("anna").orElseThrow());
            String before = Files.readString(file);
            TransactionManager failingCommit = new TransactionManager() {
                @Override
                public <T> T inTransaction(Supplier<T> operation) {
                    return storage.inTransaction(() -> {
                        operation.get();
                        throw new StorageException("Искусственный сбой перед фиксацией");
                    });
                }
            };
            var registry = new CommandRegistry();
            registry.register(new AddCategoryCommand(new CategoryService()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            var terminal = Terminal.forStreams(new ByteArrayInputStream("add-category Еда\n".getBytes(StandardCharsets.UTF_8)), output);
            new CommandLoop(terminal, session, registry, wallets, failingCommit).run();
            String text = output.toString(StandardCharsets.UTF_8);
            assertTrue(text.contains("Ошибка хранилища"));
            assertFalse(text.contains("создана"));
            assertTrue(session.wallet().getCategories().isEmpty());
            assertEquals(before, Files.readString(file));
        }
    }

    @Test
    void nextCommandRefreshesSessionAndDoesNotOverwriteAnotherInstance() {
        Path file = directory.resolve("finance.json");
        try (var first = new JsonStorage(file); var second = new JsonStorage(file)) {
            new JsonUserRepository(first).save(new User("anna", "synthetic-hash"));
            var wallets = new JsonWalletRepository(first);
            Session session = new Session();
            session.login(new User("anna", "synthetic-hash"), wallets.findByOwner("anna").orElseThrow());
            var otherWallets = new JsonWalletRepository(second);
            var updated = otherWallets.findByOwner("anna").orElseThrow();
            updated.addCategory(new Category("Такси"));
            otherWallets.save(updated);
            var registry = new CommandRegistry();
            registry.register(new AddCategoryCommand(new CategoryService()));
            var terminal = Terminal.forStreams(new ByteArrayInputStream("add-category Еда\n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream());
            new CommandLoop(terminal, session, registry, wallets, first).run();
            var result = wallets.findByOwner("anna").orElseThrow();
            assertTrue(result.hasCategory("Такси"));
            assertTrue(result.hasCategory("Еда"));
        }
    }
}
