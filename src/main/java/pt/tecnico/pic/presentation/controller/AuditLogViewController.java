package pt.tecnico.pic.presentation.controller;

import java.util.Collections;
import java.util.List;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.presentation.SceneManager;

public class AuditLogViewController {

    private final AppController appController;
    private final SceneManager sceneManager;

    private LogFilter currentFilter;

    public AuditLogViewController(AppController appController,
                                  SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
        this.currentFilter = new LogFilter();
    }

    public void initialize() {
        loadLogs();
    }

    public void loadLogs() {
        try {
            List<LogDTO> logs = appController.getLogs(currentFilter);
            showLogs(logs);

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    public void onApplyFilter() {
        loadLogs();
    }

    public void onClearFilter() {
        currentFilter = new LogFilter();
        loadLogs();
    }

    public void onRefreshClicked() {
        loadLogs();
    }

    public void showLogs(List<LogDTO> logs) {
        if (logs == null) {
            logs = Collections.emptyList();
        }

        System.out.println("Loaded " + logs.size() + " logs.");
    }

    public void showError(String message) {
        System.err.println("Error loading logs: " + message);
    }

    public LogFilter getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(LogFilter currentFilter) {
        this.currentFilter = currentFilter;
    }
    
}