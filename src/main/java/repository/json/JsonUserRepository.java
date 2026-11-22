package repository.json;

import libs.storage.StorageException;
import model.User;
import model.Wallet;
import repository.UserRepository;
import java.util.Optional;

public final class JsonUserRepository implements UserRepository {
    private final JsonStorage storage;

    public JsonUserRepository(JsonStorage storage) {
        this.storage = storage;
    }

    @Override
    public Optional<User> findByLogin(String login) {
        String normalized = User.normalizeLogin(login);
        return storage.read(state -> state.users().stream()
            .filter(user -> user.login().equals(normalized)).findFirst());
    }

    @Override
    public void save(User user) {
        storage.update(state -> {
            if (state.users().stream().anyMatch(existing -> existing.login().equals(user.login()))) {
                throw new StorageException("Пользователь уже существует: " + user.login());
            }
            state.users().add(user);
            state.wallets().add(WalletMapper.toData(new Wallet(user.login())));
            return null;
        });
    }
}
