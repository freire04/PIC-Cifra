package pt.tecnico.pic.dto;

import pt.tecnico.pic.domain.OperationResult;

public class PasswordResult {
    private final OperationResult result;
    private final char[] temporaryPassword;
    private final String message;

    public PasswordResult(OperationResult result, String message, char[] temporaryPassword) {
        this.result = result;
        this.temporaryPassword = temporaryPassword;
        this.message = message;
    }

    public OperationResult getResult() {
        return result;
    }

    public char[] getTemporaryPassword() {
        return temporaryPassword;
    }

    public String getMessage() {
        return message;
    }
}
