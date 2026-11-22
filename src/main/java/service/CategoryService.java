package service;

import model.Category;
import model.Wallet;

public class CategoryService {

    public Category addCategory(Wallet wallet, String name) {
        Category category = new Category(name);
        wallet.addCategory(category);
        return category;
    }

    public void renameCategory(Wallet wallet, String oldName, String newName) {
        wallet.renameCategory(oldName, newName);
    }

    public void removeCategory(Wallet wallet, String name) {
        wallet.removeCategory(name);
    }
}
