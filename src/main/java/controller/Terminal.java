package controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class Terminal {

    private final BufferedReader in;
    private final PrintStream out;
    private StringBuilder pending;

    private Terminal(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static Terminal forStreams(InputStream input, OutputStream output) {
        return new Terminal(
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    public void println(String text) {
        if (pending == null) {
            out.println(text);
        } else {
            pending.append(text).append(System.lineSeparator());
        }
    }

    void beginOutput() {
        pending = new StringBuilder();
    }

    void commitOutput() {
        String text = pending.toString();
        pending = null;
        out.print(text);
        out.flush();
    }

    void discardOutput() {
        pending = null;
    }

    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String ask(String prompt) {
        String line = readLine(prompt);
        if (line == null) {
            throw new InputEndedException();
        }
        return line;
    }
}
