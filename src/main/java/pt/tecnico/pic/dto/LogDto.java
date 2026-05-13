package pt.tecnico.pic.dto;

import java.time.LocalDateTime;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;

public class LogDto {
    private final LocalDateTime timestamp;
    private final String username;
    private final ActionType actionType;
    private final String filePath;
    private final OperationResult result;
    private final String message;

    public LogDto(LocalDateTime timestamp, String username, ActionType actionType, String filePath, OperationResult result, String message) {
        this.timestamp = timestamp;
        this.username = username;
        this.actionType = actionType;
        this.filePath = filePath;
        this.result = result;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getFilePath() {
        return filePath;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }
}
