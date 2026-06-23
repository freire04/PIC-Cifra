package pt.tecnico.pic.presentation.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.presentation.SceneManager;

public class RoleSelectionViewController {
    private static final List<Role> ROLE_DISPLAY_ORDER = List.of(
            Role.USER,
            Role.AUDITOR,
            Role.ADMIN
    );

    private final AppController appController;
    private final SceneManager sceneManager;

    @FXML
    private FlowPane rolesContainer;

    @FXML
    private Label errorLabel;

    public RoleSelectionViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        errorLabel.setText("");
        loadAvailableRoles();
    }

    @FXML
    public void onLogoutClicked() {
        sceneManager.logout();
    }

    private void loadAvailableRoles() {
        rolesContainer.getChildren().clear();

        Set<Role> availableRoles = appController.getAvailableRoles();

        for (Role role : rolesInDisplayOrder(availableRoles)) {
            Button roleButton = new Button(role.name());
            roleButton.setMinWidth(96);
            roleButton.setOnAction(event -> onRoleSelected(role));
            rolesContainer.getChildren().add(roleButton);
        }
    }

    public void onRoleSelected(Role role) {
        if (requiresTokenPin(role)) {
            Optional<char[]> pinResult = sceneManager.requestTokenPin();
            if (pinResult.isEmpty()) {
                return;
            }
            char[] pin = pinResult.get();

            try {
                RoleSelectionResult result = appController.selectRole(role, pin);
                handleRoleSelectionResult(result);
            } finally {
                Arrays.fill(pin, '\0');
            }
            return;
        }

        RoleSelectionResult result = appController.selectRole(role, null);
        handleRoleSelectionResult(result);
    }

    static List<Role> rolesInDisplayOrder(Set<Role> availableRoles) {
        return ROLE_DISPLAY_ORDER.stream()
                .filter(availableRoles::contains)
                .toList();
    }

    static boolean requiresTokenPin(Role role) {
        return role == Role.USER;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }

    private void handleRoleSelectionResult(RoleSelectionResult result) {
        if (result.getResult() == OperationResult.SUCCESS) {
            sceneManager.setSelectedRole(result.getSelectedRole());
            sceneManager.showDashboard();
        } else {
            showError(result.getMessage());
        }
    }

}
