package repository.json;

import model.Budget;
import model.Category;
import model.Operation;
import model.Wallet;
import java.util.List;

public final class WalletMapper {

    private WalletMapper() {
    }

    public static WalletData toData(Wallet wallet) {
        List<String> categories = wallet.getCategories().stream()
                .map(Category::name)
                .toList();
        List<WalletData.BudgetData> budgets = wallet.getBudgets().stream()
                .map(budget -> new WalletData.BudgetData(budget.category(), budget.limit()))
                .toList();
        List<WalletData.OperationData> operations = wallet.getOperations().stream()
                .map(op -> new WalletData.OperationData(op.type(), op.category(), op.amount(),
                        op.date(), op.description(), op.counterparty()))
                .toList();
        return new WalletData(wallet.getOwner(), categories, budgets, operations);
    }

    public static Wallet toWallet(WalletData data) {
        if (data == null || data.categories() == null || data.budgets() == null || data.operations() == null) {
            throw new service.ValidationException("Снимок должен содержать categories, budgets и operations.");
        }
        Wallet wallet = new Wallet(data.owner());
        for (String name : orEmpty(data.categories())) {
            wallet.addCategory(new Category(name));
        }
        var budgetKeys = new java.util.HashSet<String>();
        for (WalletData.BudgetData budget : data.budgets()) {
            if (!budgetKeys.add(new Category(budget.category()).key())) {
                throw new service.ValidationException("Повторяющийся бюджет категории: " + budget.category());
            }
            wallet.setBudget(new Budget(budget.category(), budget.limit()));
        }
        for (WalletData.OperationData op : orEmpty(data.operations())) {
            wallet.addOperation(new Operation(op.type(), op.category(), op.amount(),
                    op.date(), op.description(), op.counterparty()));
        }
        return wallet;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
