package pt.tecnico.pic.store;

public class LogStoreException extends RuntimeException {
    public LogStoreException(String message) {
        super(message);
    }

    public LogStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
