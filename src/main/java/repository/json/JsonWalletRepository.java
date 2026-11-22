package repository.json;

import libs.storage.StorageException;
import model.User;
import model.Wallet;
import repository.WalletRepository;
import java.util.Optional;

public final class JsonWalletRepository implements WalletRepository {
    private final JsonStorage storage;

    public JsonWalletRepository(JsonStorage storage) {
        this.storage = storage;
    }

    @Override
    public Optional<Wallet> findByOwner(String login) {
        String owner = User.normalizeLogin(login);
        return storage.read(state -> state.wallets().stream()
            .filter(wallet -> wallet.owner().equals(owner)).findFirst().map(WalletMapper::toWallet));
    }

    @Override
    public void save(Wallet wallet) {
        storage.update(state -> {
            if (state.users().stream().noneMatch(user -> user.login().equals(wallet.getOwner()))) {
                throw new StorageException("Неизвестный владелец кошелька: " + wallet.getOwner());
            }
            state.wallets().removeIf(existing -> existing.owner().equals(wallet.getOwner()));
            state.wallets().add(WalletMapper.toData(wallet));
            return null;
        });
    }
}
