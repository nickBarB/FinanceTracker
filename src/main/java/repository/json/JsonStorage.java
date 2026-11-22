package repository.json;

import libs.json.JsonCodec;
import libs.json.JsonFileStore;
import libs.storage.TransactionManager;
import model.User;
import model.Wallet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Один согласованный снимок пользователей и всех кошельков. */
public final class JsonStorage implements TransactionManager, AutoCloseable {
    private final JsonFileStore<State> store;

    public JsonStorage(Path file) {
        store = new JsonFileStore<>(file, JsonCodec.create(), State.class,
            () -> new State(1, new ArrayList<>(), new ArrayList<>()), State::validate);
    }

    <T> T read(Function<State, T> operation) {
        return store.read(operation);
    }

    <T> T update(Function<State, T> operation) {
        return store.update(operation);
    }

    @Override
    public <T> T inTransaction(Supplier<T> operation) {
        return store.inTransaction(operation);
    }

    @Override
    public void close() {
        store.close();
    }

    record State(int version, List<User> users, List<WalletData> wallets) {
        State {
            if (version != 1) {
                throw new IllegalArgumentException("Неподдерживаемая версия хранилища: " + version);
            }
            users = new ArrayList<>(Objects.requireNonNull(users, "users"));
            wallets = new ArrayList<>(Objects.requireNonNull(wallets, "wallets"));
        }

        void validate() {
            var logins = new HashSet<String>();
            for (User user : users) {
                if (!user.login().matches("[a-z0-9_]{3,20}") || !logins.add(user.login())) {
                    throw new IllegalArgumentException("Недопустимый или повторяющийся логин");
                }
            }
            var owners = new HashSet<String>();
            for (WalletData data : wallets) {
                Wallet wallet = WalletMapper.toWallet(data);
                if (!logins.contains(wallet.getOwner()) || !owners.add(wallet.getOwner())) {
                    throw new IllegalArgumentException("Неизвестный или повторяющийся владелец кошелька");
                }
            }
            if (!owners.equals(logins)) {
                throw new IllegalArgumentException("Каждому пользователю должен соответствовать кошелёк");
            }
        }
    }
}
