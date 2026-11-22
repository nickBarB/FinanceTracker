package controller;

public class InputEndedException extends RuntimeException {

    public InputEndedException() {
        super("Ввод завершён.");
    }
}
