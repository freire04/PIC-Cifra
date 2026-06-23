package pt.tecnico.pic.presentation.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.presentation.SceneManager;

public class AuditLogViewController {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppController appController;
    private final SceneManager sceneManager;
    private LogFilter currentFilter;

    @FXML
    private TextField usernameFilterField;
    @FXML
    private ComboBox<ActionType> actionTypeFilterComboBox;
    @FXML
    private ComboBox<OperationResult> resultFilterComboBox;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TableView<LogDTO> logsTable;
    @FXML
    private TableColumn<LogDTO, Integer> logIdColumn;
    @FXML
    private TableColumn<LogDTO, String> timestampColumn;
    @FXML
    private TableColumn<LogDTO, String> usernameColumn;
    @FXML
    private TableColumn<LogDTO, String> actorRoleColumn;
    @FXML
    private TableColumn<LogDTO, String> actionTypeColumn;
    @FXML
    private TableColumn<LogDTO, String> resultColumn;
    @FXML
    private TableColumn<LogDTO, String> fileNameColumn;
    @FXML
    private Label logCountLabel;
    @FXML
    private Label statusLabel;

    public AuditLogViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
        this.currentFilter = new LogFilter();
    }

    @FXML
    public void initialize() {
        configureFilterControls();
        configureTable();
        loadLogs();
    }

    public void loadLogs() {
        try {
            List<LogDTO> logs = appController.getAuditLogs(currentFilter);
            showLogs(logs);
        } catch (RuntimeException e) {
            showLogs(List.of());
            showError("Could not load audit logs.");
        }
    }

    @FXML
    public void onApplyFilter() {
        try {
            currentFilter = buildFilter(
                    usernameFilterField.getText(),
                    actionTypeFilterComboBox.getValue(),
                    resultFilterComboBox.getValue(),
                    startDatePicker.getValue(),
                    endDatePicker.getValue()
            );
            loadLogs();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    public void onClearFilter() {
        usernameFilterField.clear();
        actionTypeFilterComboBox.getSelectionModel().clearSelection();
        resultFilterComboBox.getSelectionModel().clearSelection();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        currentFilter = new LogFilter();
        loadLogs();
    }

    @FXML
    public void onRefreshClicked() {
        loadLogs();
    }

    @FXML
    public void onBackClicked() {
        sceneManager.showDashboard();
    }

    public void showLogs(List<LogDTO> logs) {
        List<LogDTO> safeLogs = logs == null ? List.of() : logs;
        logsTable.setItems(FXCollections.observableArrayList(safeLogs));
        logCountLabel.setText(safeLogs.size() + " shown");
        showSuccess("Audit logs loaded.");
    }

    public void showError(String message) {
        statusLabel.setText(message == null || message.isBlank() ? "Unknown error." : message);
        statusLabel.setStyle("-fx-text-fill: #991b1b;");
    }

    public LogFilter getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(LogFilter currentFilter) {
        this.currentFilter = currentFilter;
    }

    static LogFilter buildFilter(String username, ActionType actionType, OperationResult result,
                                 LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }

        String normalizedUsername = username == null || username.isBlank() ? null : username.trim();

        return new LogFilter(
                normalizedUsername,
                null,
                actionType,
                result,
                null,
                startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : LocalDateTime.of(endDate, LocalTime.MAX)
        );
    }

    static String formatTimestamp(LocalDateTime timestamp) {
        return timestamp == null ? "-" : TIMESTAMP_FORMATTER.format(timestamp);
    }

    private void configureFilterControls() {
        actionTypeFilterComboBox.setItems(FXCollections.observableArrayList(ActionType.values()));
        resultFilterComboBox.setItems(FXCollections.observableArrayList(OperationResult.values()));
    }

    private void configureTable() {
        logsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        logsTable.setPlaceholder(new Label("No audit logs found."));

        logIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getLogId()));
        timestampColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatTimestamp(cell.getValue().getTimestamp()))
        );
        usernameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(displayText(cell.getValue().getUsername()))
        );
        actorRoleColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatRole(cell.getValue().getActorRole()))
        );
        actionTypeColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatActionType(cell.getValue().getActionType()))
        );
        resultColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatResult(cell.getValue().getResult()))
        );
        fileNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(displayText(cell.getValue().getFileName()))
        );
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #166534;");
    }

    private static String formatRole(Role role) {
        return role == null ? "-" : role.name();
    }

    private static String formatActionType(ActionType actionType) {
        return actionType == null ? "-" : actionType.name();
    }

    private static String formatResult(OperationResult result) {
        return result == null ? "-" : result.name();
    }

    private static String displayText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

}
