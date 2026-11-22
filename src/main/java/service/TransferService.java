package service;

import model.Category;
import model.Money;
import model.Operation;
import model.OperationType;
import model.User;
import model.Wallet;
import repository.UserRepository;
import repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import libs.storage.TransactionManager;

public class TransferService {

    public static final String TRANSFER_CATEGORY = "Перевод";

    private final UserRepository users;
    private final WalletRepository wallets;
    private final TransactionManager transactions;
    private final Clock clock;

    public TransferService(UserRepository users, WalletRepository wallets,
                           TransactionManager transactions, Clock clock) {
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Operation transfer(Wallet sender, String recipientLogin, BigDecimal amount,
                              LocalDate date, String description) {
        OperationDates.validate(date, clock);
        Wallet[] committed = new Wallet[1];
        Operation result = transactions.inTransaction(() -> {
            Wallet current = wallets.findByOwner(sender.getOwner()).orElseGet(sender::copy);
            Operation operation = transferWithinTransaction(current, recipientLogin, amount, date, description);
            wallets.save(current);
            committed[0] = current;
            return operation;
        });
        sender.replaceWith(committed[0]);
        return result;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private Operation transferWithinTransaction(Wallet sender, String recipientLogin, BigDecimal amount,
                                               LocalDate date, String description) {
        String recipient = User.normalizeLogin(recipientLogin);
        if (recipient.isEmpty()) {
            throw new ValidationException("Логин получателя не указан.");
        }
        if (recipient.equalsIgnoreCase(sender.getOwner())) {
            throw new ValidationException("Нельзя переводить деньги самому себе.");
        }
        if (users.findByLogin(recipient).isEmpty()) {
            throw new ValidationException("Пользователь «" + recipient + "» не найден.");
        }
        BigDecimal money = Money.positive(amount);
        if (sender.getBalance().compareTo(money) < 0) {
            throw new ValidationException("Недостаточно средств: на балансе "
                    + sender.getBalance().toPlainString() + ", а для перевода нужно "
                    + money.toPlainString() + ".");
        }

        Operation expense = new Operation(
                OperationType.EXPENSE, TRANSFER_CATEGORY, money, date, description, recipient);
        Operation income = new Operation(
                OperationType.INCOME, TRANSFER_CATEGORY, money, date, description, sender.getOwner());

        Wallet target = wallets.findByOwner(recipient).orElseGet(() -> new Wallet(recipient));
        ensureTransferCategory(target);
        target.addOperation(income);
        wallets.save(target);

        ensureTransferCategory(sender);
        sender.addOperation(expense);
        return expense;
    }

    private static void ensureTransferCategory(Wallet wallet) {
        if (!wallet.hasCategory(TRANSFER_CATEGORY)) {
            wallet.addCategory(new Category(TRANSFER_CATEGORY));
        }
    }
}
