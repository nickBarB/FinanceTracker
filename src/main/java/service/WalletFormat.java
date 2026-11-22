package service;

import model.Wallet;
import model.WalletContent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public interface WalletFormat {

    String extension();

    void write(Wallet wallet, Writer out) throws IOException;

    WalletContent read(Reader in) throws IOException;
}
