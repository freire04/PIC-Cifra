package pt.tecnico.pic.presentation.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.SceneManager;

public class DashboardViewController {
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
        Role selectedRole = sceneManager.getSelectedRole();

        titleLabel.setText(selectedRole + " Dashboard");
        loadAvailableActions(selectedRole);
    }

    public void loadAvailableActions(Role selectedRole) {
        actionsContainer.getChildren().clear();

        // TODO:
        // Dashboard actions should ideally come from the AppController/session state,
        // not from SceneManager.selectedRole.
        // Current implementation uses SceneManager as temporary placeholder state.

        if (selectedRole == Role.USER) {
            addActionButton("Encrypt File", this::onEncryptSelected);
            addActionButton("Decrypt File", this::onDecryptSelected);
        }

        if (selectedRole == Role.AUDITOR) {
            // TODO: Currently empty - no actions defined for auditor role yet
        }

        if (selectedRole == Role.ADMIN) {
            addActionButton("Manage Users", this::onAdminUsersSelected);
        }
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

    public void onAdminUsersSelected() {
        sceneManager.showAdminUsers();
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