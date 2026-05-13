package pt.tecnico.pic.dto;

import java.time.LocalDateTime;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public class LogFilter {
    private String username;
    private Role role;
    private ActionType actionType;
    private OperationResult result;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public LogFilter() {}

    public LogFilter(String username, Role role, ActionType actionType, OperationResult result, LocalDateTime startDate, LocalDateTime endDate) {
        this.username = username;
        this.role = role;
        this.actionType = actionType;
        this.result = result;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public void setUsername(String username) {
        this.username = username;
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
