package pt.tecnico.pic.presentation.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.presentation.PlaceholderAppController;
import pt.tecnico.pic.presentation.SceneManager;

public class RoleSelectionViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    //PLACEHOLDER PARA TIRAR QUANDO HOUVER CONEXÃO ENTRE FRONTEND E BACKEND
    private final PlaceholderAppController placeholderAppController = new PlaceholderAppController();

    @FXML
    private HBox rolesContainer;

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
        // TODO (S1-10): appController.logout();
        sceneManager.showLogin();
    }

    private void loadAvailableRoles() {
        rolesContainer.getChildren().clear();

        Set<Role> availableRoles = placeholderAppController.getAvailableRoles();

        List<Role> orderedRoles = List.of(
                Role.USER,
                Role.AUDITOR,
                Role.ADMIN
        );

        for (Role role : orderedRoles) {
            if (!availableRoles.contains(role)) {
                continue;
            }

            Button roleButton = new Button(role.name());
            roleButton.setOnAction(event -> onRoleSelected(role));
            rolesContainer.getChildren().add(roleButton);
        }
    }

    public void onRoleSelected(Role role) {
        if (role == Role.USER) {
            Optional<char[]> pinResult = sceneManager.requestTokenPin();
            if (pinResult.isEmpty()) {
                return;
            }
            char[] pin = pinResult.get();

            try {
                RoleSelectionResult result = placeholderAppController.selectRole(role, pin);
                handleRoleSelectionResult(result);
            } finally {
                Arrays.fill(pin, '\0');
            }
            return;
        }

        RoleSelectionResult result = placeholderAppController.selectRole(role, null);
        handleRoleSelectionResult(result);
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
