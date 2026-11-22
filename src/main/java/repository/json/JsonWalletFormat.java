package repository.json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import model.Wallet;
import model.WalletContent;
import service.ValidationException;
import service.WalletFormat;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** Обмен только содержимым кошелька; учётные данные не экспортируются. */
public final class JsonWalletFormat implements WalletFormat {
    private final Gson gson;
    private final java.time.Clock clock;

    public JsonWalletFormat(Gson gson) {
        this(gson, java.time.Clock.systemDefaultZone());
    }

    public JsonWalletFormat(Gson gson, java.time.Clock clock) {
        this.gson = gson;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public String extension() {
        return "json";
    }

    @Override
    public void write(Wallet wallet, Writer out) throws IOException {
        out.write(gson.toJson(WalletMapper.toData(wallet)) + "\n");
    }

    @Override
    public WalletContent read(Reader in) throws IOException {
        try {
            WalletData data = gson.fromJson(in, WalletData.class);
            if (data == null) {
                throw new ValidationException("Файл не содержит снимка кошелька.");
            }
            Wallet wallet = WalletMapper.toWallet(data);
            wallet.getOperations().forEach(operation -> service.OperationDates.validate(operation.date(), clock));
            return new WalletContent(wallet.getCategories(), wallet.getBudgets(), wallet.getOperations());
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new ValidationException("Некорректный JSON-снимок кошелька: " + e.getMessage());
        }
    }
}
