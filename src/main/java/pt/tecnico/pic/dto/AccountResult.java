package pt.tecnico.pic.dto;

import pt.tecnico.pic.domain.OperationResult;

public class AccountResult {
    private final OperationResult result;
    private final String message;

    public AccountResult(OperationResult result, String message) {
        this.result = result;
        this.message = message;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }
}
