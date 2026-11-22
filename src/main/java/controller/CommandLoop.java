package controller;

import service.CategoryNotFoundException;
import libs.storage.StorageException;
import service.ValidationException;
import repository.WalletRepository;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import libs.storage.TransactionManager;
import model.User;
import model.Wallet;

public class CommandLoop {

    private static final Logger LOG = Logger.getLogger(CommandLoop.class.getName());

    private final Terminal terminal;
    private final Session session;
    private final CommandRegistry registry;
    private final WalletRepository wallets;
    private final TransactionManager transactions;

    public CommandLoop(
            Terminal terminal, Session session, CommandRegistry registry, WalletRepository wallets,
            TransactionManager transactions) {
        this.terminal = terminal;
        this.session = session;
        this.registry = registry;
        this.wallets = wallets;
        this.transactions = transactions;
    }

    public void run() {
        terminal.println("FinanceTracker: учёт личных финансов. Введите help, чтобы увидеть команды.");
        try {
            while (session.isRunning()) {
                String text = terminal.readLine(prompt());
                if (text == null) {
                    break;
                }
                handle(text);
            }
        } catch (InputEndedException e) {
            // ввод закончился посреди вопроса, выходим как при обычном конце ввода
        }
        shutdown();
    }

    private void handle(String text) {
        try {
            CommandLine line = CommandLine.parse(text);
            if (line.isEmpty()) {
                return;
            }
            Optional<Command> found = registry.find(line.name());
            if (found.isEmpty()) {
                terminal.println("Неизвестная команда «" + line.name()
                        + "». Введите help, чтобы увидеть список.");
                return;
            }
            Command command = found.get();
            if (command.requiresLogin() && !session.isLoggedIn()) {
                terminal.println("Эта команда доступна после входа. Выполните login или register.");
                return;
            }
            executeAtomically(command, line);
        } catch (InputEndedException e) {
            throw e;
        } catch (CategoryNotFoundException e) {
            terminal.println("Ошибка: " + e.getMessage());
            terminal.println("Создать категорию можно командой: add-category \""
                    + e.getCategoryName() + "\"");
        } catch (ValidationException e) {
            terminal.println("Ошибка: " + e.getMessage());
        } catch (StorageException e) {
            LOG.log(Level.SEVERE, "Ошибка хранилища при выполнении команды", e);
            terminal.println("Ошибка хранилища: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Непредвиденная ошибка команды", e);
            terminal.println("Непредвиденная ошибка: " + e);
        }
    }

    private String prompt() {
        return session.isLoggedIn() ? session.user().login() + "> " : "> ";
    }

    private void executeAtomically(Command command, CommandLine line) {
        User previousUser = session.isLoggedIn() ? session.user() : null;
        Wallet previousWallet = session.isLoggedIn() ? session.wallet().copy() : null;
        terminal.beginOutput();
        try {
            transactions.inTransaction(() -> {
                if (session.isLoggedIn()) {
                    wallets.findByOwner(session.user().login())
                        .ifPresent(current -> session.login(session.user(), current));
                }
                command.execute(line, session, terminal);
                if (command.changesData() && session.isLoggedIn()) {
                    wallets.save(session.wallet());
                }
                return null;
            });
            terminal.commitOutput();
        } catch (RuntimeException e) {
            terminal.discardOutput();
            if (previousUser == null) {
                session.logout();
            } else {
                session.login(previousUser, previousWallet);
            }
            throw e;
        }
    }

    private void shutdown() {
        // Каждая изменяющая команда уже зафиксирована. Устаревшая сессия не пишет на диск.
        terminal.println("До свидания!");
    }
}
