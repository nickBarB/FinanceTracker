package service;

import model.Budget;
import model.Wallet;
import java.math.BigDecimal;

public class BudgetService {

    public Budget setBudget(Wallet wallet, String category, BigDecimal limit) {
        Budget budget = new Budget(category, limit);
        wallet.setBudget(budget);
        return budget;
    }

    public void removeBudget(Wallet wallet, String category) {
        wallet.removeBudget(category);
    }
}
