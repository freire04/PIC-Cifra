package pt.tecnico.pic.dto;

import java.time.LocalDateTime;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public class LogFilter {
    private String username;
    private Role actorRole;
    private ActionType actionType;
    private OperationResult result;
    private String fileName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public LogFilter() {}

    public LogFilter(String username, Role actorRole, ActionType actionType, OperationResult result,
                     String fileName, LocalDateTime startDate, LocalDateTime endDate) {
        this.username = username;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.result = result;
        this.fileName = fileName;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getActorRole() {
        return actorRole;
    }

    public void setActorRole(Role actorRole) {
        this.actorRole = actorRole;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public OperationResult getResult() {
        return result;
    }

    public void setResult(OperationResult result) {
        this.result = result;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }
}