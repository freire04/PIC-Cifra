package pt.tecnico.pic.presentation;

import java.util.Objects;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;

/**
 * Temporary programmatic placeholder views for S1-11.
 *
 * These screens exist only to validate JavaFX startup and navigation.
 * Real FXML views and ViewControllers will replace this UI in later issues.
 */

// TODO: call appController.logout() when session handling is implemented.

public class SceneManager {
    private final Stage primaryStage;
    private final AppController appController;

    public SceneManager(Stage primaryStage, AppController appController) {
        this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage must not be null");
        this.appController = Objects.requireNonNull(appController, "appController must not be null");
    }

    public void showLogin() {        
        Label title = new Label("Login");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(240);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(240);

        Button loginButton = new Button("Login");
        loginButton.setOnAction(event -> showRoleSelection());

        Button temporaryPasswordButton = new Button("Login with temporary password");
        temporaryPasswordButton.setOnAction(event -> showChangePassword(true));

        VBox root = new VBox(12, title, usernameField, passwordField, loginButton, temporaryPasswordButton);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }

    public void showRoleSelection() {
        Label title = new Label("Select Role");

        Button userButton = new Button("USER");
        userButton.setOnAction(event -> showPinDialog());

        Button auditorButton = new Button("AUDITOR");
        auditorButton.setOnAction(event -> showAuditLogs());

        Button adminButton = new Button("ADMIN");
        adminButton.setOnAction(event -> showAdminUsers());

        HBox roleButtons = new HBox(16, userButton, auditorButton, adminButton);
        roleButtons.setAlignment(Pos.CENTER);

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        VBox root = new VBox(20, title, roleButtons, logoutButton);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }

    private void showPinDialog() {
        Dialog<String> pinDialog = new Dialog<>();
        pinDialog.setTitle("Token PIN");
        pinDialog.setHeaderText("Enter your 6-digit PIN");

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("6-digit PIN");

        pinDialog.getDialogPane().setContent(pinField);

        ButtonType confirmButton =
                new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);

        pinDialog.getDialogPane()
                .getButtonTypes()
                .addAll(confirmButton, ButtonType.CANCEL);

        pinDialog.setResultConverter(button -> {
            if (button == confirmButton) {
                return pinField.getText();
            }
            return null;
        });

        pinDialog.showAndWait().ifPresent(pin -> showDashboard());
    }

    public void showDashboard() {
        Label title = new Label("Main Menu");

        Button encryptButton = new Button("Encrypt File");
        encryptButton.setOnAction(event -> showEncryptionView());

        Button decryptButton = new Button("Decrypt File");
        decryptButton.setOnAction(event -> showDecryptionView());

        Button changeRoleButton = new Button("Change Role");
        changeRoleButton.setOnAction(event -> showRoleSelection());

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        VBox root = new VBox(16, title, encryptButton, decryptButton, changeRoleButton, logoutButton);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }

    public void showEncryptionView() {
        showFileCryptoPlaceholder(
                "Encrypt File",
                "Drop file to encrypt here",
                "Encrypt",
                true
        );
    }

    public void showDecryptionView() {
        showFileCryptoPlaceholder(
                "Decrypt File",
                "Drop file to decrypt here",
                "Decrypt",
                false
        );
    }

    public void showAuditLogs() {
        Label title = new Label("Audit Logs View");

        Button backButton = new Button("Change Role");
        backButton.setOnAction(event -> showRoleSelection());

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        HBox buttons = new HBox(12, backButton, logoutButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, title, buttons);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }

    public void showAdminUsers() {
        Label title = new Label("Admin Users View");

        Button backButton = new Button("Change Role");
        backButton.setOnAction(event -> showRoleSelection());

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        HBox buttons = new HBox(12, backButton, logoutButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, title, buttons);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }

    public void showChangePassword(boolean required) {
        Label title = new Label("Change Password");

        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Current password");
        oldPasswordField.setMaxWidth(240);

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        newPasswordField.setMaxWidth(240);

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");
        confirmPasswordField.setMaxWidth(240);

        Button confirmButton = new Button("Change Password");
        confirmButton.setOnAction(event -> {
            if (required) {
                showRoleSelection();
            } else {
                showDashboard();
            }
        });

        Button cancelButton = new Button(required ? "Logout" : "Back");
        cancelButton.setOnAction(event -> {
            if (required) {
                logout();
            } else {
                showDashboard();
            }
        });

        VBox root = new VBox(12, title, oldPasswordField, newPasswordField,
                confirmPasswordField, confirmButton, cancelButton);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 360));
    }
    
    public void logout() {
        // appController.logout();
        showLogin();
    }

    private void setScene(Scene scene) {
        primaryStage.setScene(scene);
    }

    private void showFileCryptoPlaceholder(String titleText,
                                        String dropText,
                                        String actionText,
                                        boolean encryptionMode) {

        Label title = new Label(titleText);
        Label dropLabel = new Label(dropText);

        VBox dropArea = new VBox(dropLabel);
        dropArea.setAlignment(Pos.CENTER);
        dropArea.setPrefSize(420, 180);
        dropArea.setMinSize(420, 180);
        dropArea.setMaxSize(420, 180);
        dropArea.setStyle(
                "-fx-border-color: #9ca3af;" +
                "-fx-border-style: dashed;" +
                "-fx-border-width: 2;" +
                "-fx-background-color: #f9fafb;"
        );

        Button browseButton = new Button("Browse");
        Button actionButton = new Button(actionText);
        Button switchModeButton = new Button(
                encryptionMode
                        ? "Switch to Decryption"
                        : "Switch to Encryption"
        );

        switchModeButton.setOnAction(event -> {
            if (encryptionMode) {
                showDecryptionView();
            } else {
                showEncryptionView();
            }
        });

        Button backButton = new Button("Back");
        backButton.setOnAction(event -> showDashboard());

        HBox buttons = new HBox(
                12,
                browseButton,
                actionButton,
                switchModeButton,
                backButton
        );

        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, title, dropArea, buttons);
        root.setAlignment(Pos.CENTER);

        setScene(new Scene(root, 640, 420));
    }

}
