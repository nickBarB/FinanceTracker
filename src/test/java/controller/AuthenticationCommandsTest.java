package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import libs.security.Pbkdf2PasswordHasher;
import libs.storage.StorageException;
import model.Budget;
import model.Category;
import model.Operation;
import model.OperationType;
import model.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import repository.memory.InMemoryUserRepository;
import service.AuthService;
import service.NotificationService;
import service.StatisticsService;
import service.ValidationException;
import support.TestClock;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

class AuthenticationCommandsTest {
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final RecordingWalletRepository wallets = new RecordingWalletRepository();
    private final Session session = new Session();
    private final AuthService auth = new AuthService(users, new Pbkdf2PasswordHasher(1000));
    private final Command register = new RegisterCommand(auth, wallets);
    private final Command login = new LoginCommand(auth, wallets, new NotificationService(new StatisticsService()));
    private final Command logout = new LogoutCommand(wallets);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private String execute(Command command, String input) {
        output.reset();
        Terminal terminal = Terminal.forStreams(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);
        command.execute(CommandLine.parse(command.name()), session, terminal);
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void registerLogoutAndLoginPreserveWalletAndIdentity() {
        String registered = execute(register, "Anna\nsecret1\n");
        assertEquals("anna", session.user().login());
        assertEquals("anna", session.wallet().getOwner());
        assertTrue(registered.contains("add-category"));
        Wallet original = session.wallet();
        original.addCategory(new Category("Еда"));

        assertTrue(execute(logout, "").contains("Вы вышли"));
        assertFalse(session.isLoggedIn());
        assertSame(original, wallets.stored.get("anna"));
        assertThrows(ValidationException.class, session::wallet);
        assertThrows(ValidationException.class, session::user);

        assertTrue(execute(login, "ANNA\nsecret1\n").contains("Добро пожаловать"));
        assertSame(original, session.wallet());
        assertTrue(session.wallet().hasCategory("Еда"));
    }

    @Test
    void loginDisplaysBalanceAndCurrentBudgetAlerts() {
        auth.register("anna", "secret1");
        Wallet wallet = new Wallet("anna");
        wallet.addCategory(new Category("Еда"));
        wallet.addCategory(new Category("Зарплата"));
        wallet.setBudget(new Budget("Еда", new BigDecimal("500")));
        wallet.addOperation(new Operation(OperationType.INCOME, "Зарплата", new BigDecimal("10000"),
            TestClock.TODAY, "", null));
        wallet.addOperation(new Operation(OperationType.EXPENSE, "Еда", new BigDecimal("1000"),
            TestClock.TODAY, "", null));
        wallets.stored.put("anna", wallet);

        String text = execute(login, "anna\nsecret1\n");
        assertSame(wallet, session.wallet());
        assertTrue(text.contains("9,000.00"));
        assertTrue(text.contains("Превышен бюджет"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login"})
    void authenticatedSessionRejectsAnotherLoginBeforePrompting(String command) {
        execute(register, "anna\nsecret1\n");
        assertThrows(ValidationException.class, () -> execute(command.equals("register") ? register : login, ""));
        assertEquals("anna", session.user().login());
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("Логин:"));
    }

    @Test
    void interruptedRegistrationStoresNoUser() {
        assertThrows(InputEndedException.class, () -> execute(register, "anna\n"));
        assertFalse(session.isLoggedIn());
        assertTrue(users.findByLogin("anna").isEmpty());
    }

    @Test
    void failedLogoutPreservesAuthenticatedSession() {
        execute(register, "anna\nsecret1\n");
        wallets.failure = new StorageException("диск полон");
        assertThrows(StorageException.class, () -> execute(logout, ""));
        assertTrue(session.isLoggedIn());
    }

    @Test
    void failedWalletLoadDoesNotOpenSession() {
        auth.register("anna", "secret1");
        wallets.loadFailure = new StorageException("файл повреждён");
        assertThrows(StorageException.class, () -> execute(login, "anna\nsecret1\n"));
        assertFalse(session.isLoggedIn());
    }
}
