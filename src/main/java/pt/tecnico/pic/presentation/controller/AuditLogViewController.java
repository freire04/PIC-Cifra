package pt.tecnico.pic.presentation.controller;

import java.util.List;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.presentation.SceneManager;

public class AuditLogViewController {

    private final AppController appController;
    private final SceneManager sceneManager;
    private LogFilter currentFilter;

    public AuditLogViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
        this.currentFilter = new LogFilter();
    }

    public void initialize() {
        if (appController.recordAuditLogsAccess() != OperationResult.SUCCESS) {
            showError("Audit logs are not available for the active role.");
        }
    }

    public void loadLogs() {}

    public void onApplyFilter() {}

    public void onClearFilter() {}

    public void onRefreshClicked() {}

    public void showLogs(List<LogDTO> logs) {}

    public void showError(String message) {}

    public LogFilter getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(LogFilter currentFilter) {
        this.currentFilter = currentFilter;
    }

}
