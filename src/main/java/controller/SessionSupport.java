package controller;

import model.User;
import model.Wallet;
import repository.WalletRepository;

final class SessionSupport {

    private SessionSupport() {
    }

    static Wallet open(Session session, User user, WalletRepository wallets) {
        Wallet wallet = wallets.findByOwner(user.login())
                .orElseGet(() -> new Wallet(user.login()));
        session.login(user, wallet);
        return wallet;
    }
}
