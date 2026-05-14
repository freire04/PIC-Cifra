package pt.tecnico.pic.domain;

import java.time.LocalDateTime;

public final class Log {
    private final int logId;
    private final LocalDateTime timestamp;
    private final Integer accountId;
    private final String username;
    private final Role actorRole;
    private final ActionType action;
    private final String filePath;
    private final OperationResult result;
    private final String message;

    public Log(int logId, Integer accountId, String username, Role actorRole, ActionType action,
               String filePath, LocalDateTime timestamp, OperationResult result, String message) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.accountId = accountId;
        this.username = username;
        this.actorRole = actorRole;
        this.action = action;
        this.filePath = filePath;
        this.result = result;
        this.message = message;
    }

    public Log(int logId, LocalDateTime timestamp, Integer accountId, String username, Role actorRole,
               ActionType action, String filePath, OperationResult result, String message) {
        this(logId, accountId, username, actorRole, action, filePath, timestamp, result, message);
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

    public Role getRole() {
        return actorRole;
    }

    public ActionType getAction() {
        return action;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return sanitizeFileName(filePath);
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    private static String sanitizeFileName(String fileNameOrPath) {
        if (fileNameOrPath == null || fileNameOrPath.isBlank()) {
            return null;
        }

        String normalizedPath = fileNameOrPath.replace('\\', '/');
        int lastSeparator = normalizedPath.lastIndexOf('/');
        return lastSeparator >= 0 ? normalizedPath.substring(lastSeparator + 1) : normalizedPath;
    }
}
