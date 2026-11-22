package repository;

import model.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByOwner(String login);

    void save(Wallet wallet);
}
