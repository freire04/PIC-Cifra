package pt.tecnico.pic.presentation.controller;

import java.util.Arrays;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.presentation.SceneManager;

public class ChangePasswordViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    @FXML
    private PasswordField oldPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label errorLabel;

    public ChangePasswordViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    @FXML
    public void onChangePasswordClicked() {
        char[] oldPassword = oldPasswordField.getText().toCharArray();
        char[] newPassword = newPasswordField.getText().toCharArray();
        char[] confirmPassword = confirmPasswordField.getText().toCharArray();

        try {
            if (!Arrays.equals(newPassword, confirmPassword)) {
                clearFields();
                showError("New passwords do not match.");
                return;
            }

            AccountResult changePasswordResult = appController.changeOwnPassword(oldPassword, newPassword);

            if (changePasswordResult.getResult() != OperationResult.SUCCESS) {
                clearFields();
                showError(changePasswordResult.getMessage());
                return;
            }

            clearFields();
            sceneManager.showRoleSelection();
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }

    public void clearFields() {
        oldPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("");
    }
}
