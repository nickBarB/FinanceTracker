
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import libs.storage.StorageException;
import repository.json.JsonStorage;
import repository.json.JsonWalletRepository;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationTest {

    @TempDir
    Path tempDir;

    private Path dataDir;

    @BeforeEach
    void setUp() {
        dataDir = tempDir.resolve("data");
    }

    private String run(String... lines) {
        String input = String.join("\n", lines) + "\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Application(new config.ApplicationConfig(dataDir.resolve("finance.json"), tempDir.resolve("logs"), 1000),
                support.TestClock.CLOCK)
                .run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void completeSessionReportsIncomeExpensesAndBudgets() {
        String out = run(
                "register", "anna", "secret1",
                "add-category Еда", "add-category Развлечения",
                "add-category \"Коммунальные услуги\"", "add-category Такси",
                "add-category Зарплата", "add-category Бонус",
                "set-budget Еда 4000", "set-budget Развлечения 3000",
                "set-budget \"Коммунальные услуги\" 2500",
                "add-expense Еда 300", "add-expense Еда 500",
                "add-expense Развлечения 3000",
                "add-expense \"Коммунальные услуги\" 3000", "add-expense Такси 1500",
                "add-income Зарплата 20000", "add-income Зарплата 40000",
                "add-income Бонус 3000",
                "report", "exit");

        assertTrue(out.contains("Общий доход: 63,000.00"));
        assertTrue(out.contains("Общие расходы: 8,300.00"));
        assertTrue(out.contains("3,200.00"));
        assertTrue(out.contains("-500.00"));
        assertTrue(out.contains("Превышен бюджет «Коммунальные услуги»"));
        assertTrue(Files.exists(dataDir.resolve("finance.json")));
    }

    @Test
    void dataSurvivesRestartAndIsLoadedAtLogin() {
        run("register", "anna", "secret1", "add-category Зарплата",
                "add-income Зарплата 100", "exit");

        String out = run("login", "anna", "secret1", "history", "exit");

        assertTrue(out.contains("Баланс: 100.00"));
        assertTrue(out.contains("Зарплата"));
        assertTrue(out.contains("Всего операций: 1."));
    }

    @Test
    void categoryAndBudgetEditsSurviveRestart() {
        run("register", "anna", "secret1", "add-category Еда", "add-category Такси",
            "add-category Временная", "set-budget Еда 4000", "add-expense Еда 300",
            "rename-category Еда Продукты", "set-budget Продукты 5000",
            "remove-category Временная", "exit");

        try (var storage = new JsonStorage(dataDir.resolve("finance.json"))) {
            var wallet = new JsonWalletRepository(storage).findByOwner("anna").orElseThrow();
            assertTrue(wallet.hasCategory("Продукты"));
            assertFalse(wallet.hasCategory("Еда"));
            assertFalse(wallet.hasCategory("Временная"));
            assertEquals("Продукты", wallet.getOperations().get(0).category());
            assertEquals(new BigDecimal("5000.00"),
                wallet.getBudget("Продукты").orElseThrow().limit());
        }

        run("login", "anna", "secret1", "remove-budget Продукты", "remove-category Такси", "exit");
        try (var storage = new JsonStorage(dataDir.resolve("finance.json"))) {
            var wallet = new JsonWalletRepository(storage).findByOwner("anna").orElseThrow();
            assertTrue(wallet.getBudgets().isEmpty());
            assertFalse(wallet.hasCategory("Такси"));
            assertEquals(1, wallet.getOperations().size());
        }
    }

    @Test
    void transferReachesRecipientEvenWhenOffline() {
        run("register", "boris", "secret2", "exit");
        run("register", "anna", "secret1", "add-category Зарплата",
                "add-income Зарплата 1000", "transfer boris 300 \"за обед\"", "exit");

        String out = run("login", "boris", "secret2", "history", "exit");

        assertTrue(out.contains("Баланс: 300.00"));
        assertTrue(out.contains("от: anna"));
    }

    @Test
    void wrongPasswordDoesNotOpenSession() {
        String out = run("register", "anna", "secret1", "logout",
                "login", "anna", "wrong-pass", "categories", "exit");

        assertTrue(out.contains("Неверный логин или пароль"));
        assertTrue(out.contains("доступна после входа"));
    }

    @Test
    void walletIsSavedEvenWhenInputEndsWithoutExit() throws IOException {
        String out = run("register", "anna", "secret1", "add-category Еда");

        assertTrue(out.contains("До свидания!"));
        assertTrue(Files.readString(dataDir.resolve("finance.json"))
                .contains("Еда"));
    }

    @Test
    void errorsDoNotStopTheApplication() {
        String out = run("register", "anna", "secret1", "add-expense Еда abc", "foo",
                "add-category Еда", "exit");

        assertTrue(out.contains("Сумма должна быть числом"));
        assertTrue(out.contains("Неизвестная команда «foo»"));
        assertTrue(out.contains("«Еда» создана"));
    }

    @Test
    void corruptedStorageStopsStartupWithClearError() throws IOException {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("finance.json"), "{bad");

        assertThrows(StorageException.class, () -> run("exit"));
    }

    @Test
    void helpBeforeLoginListsOnlyAvailableCommands() {
        String out = run("help", "exit");

        assertTrue(out.contains("register"));
        assertTrue(out.contains("login"));
        assertFalse(out.contains("add-expense"));
    }

    @Test
    void jsonSnapshotMovesBetweenUsers() {
        Path backup = tempDir.resolve("backup.json");
        run("register", "anna", "secret1", "add-category Зарплата", "add-category Еда",
                "set-budget Еда 4000", "add-income Зарплата 1000", "export \"" + backup + "\"", "exit");

        String out = run("register", "boris", "secret2", "import \"" + backup + "\"",
                "report", "exit");

        assertTrue(out.contains("Созданы категории: Зарплата, Еда."));
        assertTrue(out.contains("4,000.00"));
        assertTrue(out.contains("Общий доход: 1,000.00"));
    }

    @Test
    void csvExportCanBeImportedAgain() {
        Path file = tempDir.resolve("ops.csv");
        run("register", "anna", "secret1", "add-category Зарплата",
                "add-income Зарплата 1000", "export \"" + file + "\"", "exit");

        String out = run("register", "boris", "secret2", "import \"" + file + "\"",
                "report", "exit");

        assertTrue(out.contains("добавлено операций — 1"));
        assertTrue(out.contains("Общий доход: 1,000.00"));
    }
}
