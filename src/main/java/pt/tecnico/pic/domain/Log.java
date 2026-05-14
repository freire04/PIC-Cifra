package pt.tecnico.pic.domain;

import java.time.LocalDateTime;

public final class Log {
    private final int logId;
    private final Integer accountId;
    private final LocalDateTime timestamp;
    private final String username;
    private final Role actorRole;
    private final ActionType action;
    private final String fileName;
    private final OperationResult result;
    private final String message;

    public Log(int logId,
               Integer accountId,
               LocalDateTime timestamp,
               String username,
               Role actorRole,
               ActionType action,
               String fileName,
               OperationResult result,
               String message) {
        this.logId = logId;
        this.accountId = accountId;
        this.timestamp = timestamp;
        this.username = username;
        this.actorRole = actorRole;
        this.action = action;
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

    public Role getActorRole() {
        return actorRole;
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
