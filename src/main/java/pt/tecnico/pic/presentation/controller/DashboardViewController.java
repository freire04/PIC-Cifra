package pt.tecnico.pic.presentation.controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.SceneManager;

public class DashboardViewController {
    enum DashboardAction {
        ENCRYPT_FILE("Encrypt File"),
        DECRYPT_FILE("Decrypt File"),
        VIEW_LOGS("View Logs"),
        MANAGE_USERS("Manage Users"),
        CHANGE_PASSWORD("Change Password");

        private final String label;

        DashboardAction(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }
    }

    private final AppController appController;
    private final SceneManager sceneManager;

    @FXML
    private Label titleLabel;

    @FXML
    private VBox actionsContainer;

    public DashboardViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        Role selectedRole = appController.getSelectedRole();

        titleLabel.setText(selectedRole == null ? "Dashboard" : selectedRole + " Dashboard");
        loadAvailableActions(selectedRole);
    }

    public void loadAvailableActions(Role selectedRole) {
        actionsContainer.getChildren().clear();

        for (DashboardAction action : actionsFor(selectedRole)) {
            addActionButton(action.getLabel(), actionHandler(action));
        }
    }

    static List<DashboardAction> actionsFor(Role selectedRole) {
        return switch (selectedRole) {
            case USER -> List.of(
                    DashboardAction.ENCRYPT_FILE,
                    DashboardAction.DECRYPT_FILE,
                    DashboardAction.CHANGE_PASSWORD
            );
            case AUDITOR -> List.of(
                    DashboardAction.VIEW_LOGS,
                    DashboardAction.CHANGE_PASSWORD
            );
            case ADMIN -> List.of(
                    DashboardAction.MANAGE_USERS,
                    DashboardAction.CHANGE_PASSWORD
            );
            case null -> List.of();
        };
    }

    private Runnable actionHandler(DashboardAction action) {
        return switch (action) {
            case ENCRYPT_FILE -> this::onEncryptSelected;
            case DECRYPT_FILE -> this::onDecryptSelected;
            case VIEW_LOGS -> this::onAuditLogsSelected;
            case MANAGE_USERS -> this::onAdminUsersSelected;
            case CHANGE_PASSWORD -> this::onChangePasswordSelected;
        };
    }

    private void addActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        actionsContainer.getChildren().add(button);
    }

    public void onEncryptSelected() {
        sceneManager.showEncryptionView();
    }

    public void onDecryptSelected() {
        sceneManager.showDecryptionView();
    }

    public void onAuditLogsSelected() {
        sceneManager.showAuditLogs();
    }

    public void onAdminUsersSelected() {
        sceneManager.showAdminUsers();
    }

    public void onChangePasswordSelected() {
        sceneManager.showChangePassword(false);
    }

    // TEMPORARY PLACEHOLDERS FOR ROLE SELECTION
    @FXML
    public void onChangeRoleSelected() {
        sceneManager.showRoleSelection();
    }

    @FXML
    public void onLogoutClicked() {
        sceneManager.logout();
    }
}
