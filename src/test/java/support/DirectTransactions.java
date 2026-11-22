package support;

import libs.storage.TransactionManager;
import java.util.function.Supplier;

/** Только для тестов команд с репозиториями в памяти. */
public final class DirectTransactions implements TransactionManager {
    @Override
    public <T> T inTransaction(Supplier<T> operation) {
        return operation.get();
    }
}
