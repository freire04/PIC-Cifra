package pt.tecnico.pic.dto;

import java.util.Arrays;

import pt.tecnico.pic.domain.OperationResult;

public class AccountCreationResult {
    private final OperationResult result;
    private final int accountId;
    private final String username;
    private char[] temporaryPassword;
    private final String message;

    public AccountCreationResult(OperationResult result, int accountId, String username, char[] temporaryPassword, String message) {
        this.result = result;
        this.accountId = accountId;
        this.username = username;
        this.temporaryPassword = temporaryPassword == null
                ? null
                : Arrays.copyOf(temporaryPassword, temporaryPassword.length);
        this.message = message;
    }

    public OperationResult getResult() {
        return result;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public char[] getTemporaryPassword() {
        return temporaryPassword == null ? null : java.util.Arrays.copyOf(temporaryPassword, temporaryPassword.length);
    }

    public void clearTemporaryPassword() {
        if (temporaryPassword != null) {
            Arrays.fill(temporaryPassword, '\0');
            temporaryPassword = null;
        }
    }

    public String getMessage() {
        return message;
    }
}
