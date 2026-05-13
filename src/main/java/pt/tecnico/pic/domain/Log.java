package pt.tecnico.pic.domain;

import java.time.LocalDateTime;

public final class Log {
    private final int logId;
    private final LocalDateTime timestamp;
    private final Integer accountId;
    private final String username;
    private final Role role;
    private final ActionType action;
    private final String fileName;
    private final OperationResult result;
    private final String message;

public Log( int logId, LocalDateTime timestamp, Integer accountId,String username, Role actorRole, 
        ActionType actionType, String fileName, OperationResult result, String message) {
    this.logId = logId;
    this.timestamp = timestamp;
    this.accountId = accountId;
    this.username = username;
    this.role = actorRole;
    this.action = actionType;
    this.fileName = fileName;
    this.result = result;
    this.message = message;
}

    public int getLogId() {
        return logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public ActionType getAction() {
        return action;
    }

    public String getFileName() {
        return fileName;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

}
