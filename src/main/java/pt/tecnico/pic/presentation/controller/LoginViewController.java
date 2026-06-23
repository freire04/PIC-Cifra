package pt.tecnico.pic.presentation.controller;

import java.util.Arrays;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.presentation.SceneManager;

public class LoginViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    public LoginViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    @FXML
    public void onLoginClicked() {
        String username = usernameField.getText();
        char[] password = passwordField.getText().toCharArray();

        try{
            LoginResult loginResult = appController.login(username, password);

            if (loginResult.getResult() != OperationResult.SUCCESS) {
                showError(loginResult.getMessage());
                passwordField.clear();
                return;
            }

            clearFields();

            if (loginResult.mustChangePassword()) {
                sceneManager.showChangePassword();
            } else {
                sceneManager.showRoleSelection();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: red;");
    }

    public void clearFields() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
    }
}