package pt.tecnico.pic.dto;

import java.time.LocalDateTime;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public class LogDto {
    private final int logId;
    private final String username;
    private final Role role;
    private final ActionType actionType;
    private final String fileName;
    private final LocalDateTime timestamp;
    private final OperationResult result;

    public LogDto(int logId, String username, Role role, ActionType actionType, String fileName, LocalDateTime timestamp, OperationResult result) {
        this.logId = logId;
        this.username = username;
        this.role = role;
        this.actionType = actionType;
        this.fileName = fileName;
        this.timestamp = timestamp;
        this.result = result;
    }

    public int getLogId() {
        return logId;
    }  

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public OperationResult getResult() {
        return result;
    }

}
