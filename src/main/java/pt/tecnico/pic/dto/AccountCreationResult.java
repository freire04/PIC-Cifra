package pt.tecnico.pic.dto;

import pt.tecnico.pic.domain.OperationResult;

public class AccountCreationResult {
    private final OperationResult result;
    private final int accountId;
    private final String username;
    private final String temporaryPassword;
    private final String message;

    public AccountCreationResult(OperationResult result, int accountId, String username, String temporaryPassword, String message) {
        this.result = result;
        this.accountId = accountId;
        this.username = username;
        this.temporaryPassword = temporaryPassword;
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

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public String getMessage() {
        return message;
    }
}
