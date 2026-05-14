package pt.tecnico.pic.dto;

import java.time.LocalDateTime;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public class LogDTO {
    private final int logId;
    private final LocalDateTime timestamp;
    private final String username;
    private final Role actorRole;
    private final ActionType actionType;
    private final String fileName;
    private final OperationResult result;
    private final String message;

    public LogDTO(int logId, LocalDateTime timestamp, String username, Role actorRole, ActionType actionType,
                  String fileNameOrPath, OperationResult result, String message) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.username = username;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.fileName = sanitizeFileName(fileNameOrPath);
        this.result = result;
        this.message = message;
    }

    public static LogDTO fromLog(Log log) {
        return new LogDTO(log.getLogId(), log.getTimestamp(), log.getUsername(), log.getActorRole(), log.getAction(),
                log.getFilePath(), log.getResult(), log.getMessage());
    }

    public int getLogId() {
        return logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public Role getActorRole() {
        return actorRole;
    }

    public ActionType getActionType() {
        return actionType;
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

    private static String sanitizeFileName(String fileNameOrPath) {
        if (fileNameOrPath == null || fileNameOrPath.isBlank()) {
            return null;
        }

        String normalizedPath = fileNameOrPath.replace('\\', '/');
        int lastSeparator = normalizedPath.lastIndexOf('/');
        return lastSeparator >= 0 ? normalizedPath.substring(lastSeparator + 1) : normalizedPath;
    }
}
