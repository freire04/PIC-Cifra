package pt.tecnico.pic.presentation;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.controller.AdminViewController;
import pt.tecnico.pic.presentation.controller.AuditLogViewController;
import pt.tecnico.pic.presentation.controller.ChangePasswordViewController;
import pt.tecnico.pic.presentation.controller.DashboardViewController;
import pt.tecnico.pic.presentation.controller.FileDecryptionViewController;
import pt.tecnico.pic.presentation.controller.FileEncryptionViewController;
import pt.tecnico.pic.presentation.controller.LoginViewController;
import pt.tecnico.pic.presentation.controller.RoleSelectionViewController;

/**
 * Temporary programmatic placeholder views for S1-11.
 *
 * These screens exist only to validate JavaFX startup and navigation.
 * Real FXML views and ViewControllers will replace this UI in later issues.
 */

public class SceneManager {
    private final Stage primaryStage;
    private final AppController appController;

    private static final int WINDOW_WIDTH = 720;
    private static final int WINDOW_HEIGHT = 520;

    private Role selectedRole; //TEMPORARY PLACEHOLDER

    public SceneManager(Stage primaryStage, AppController appController) {
        this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage must not be null");
        this.appController = Objects.requireNonNull(appController, "appController must not be null");
    }

    public void showLogin() {   
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            LoginViewController controller = new LoginViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load LoginView.fxml", e);
        }
    }

    public void showChangePassword() {
        showChangePassword(true);
    }

    public void showChangePassword(boolean mandatoryChange) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChangePasswordView.fxml"));
            ChangePasswordViewController controller = new ChangePasswordViewController(appController, this, mandatoryChange);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load ChangePasswordView.fxml", e);
        }
    }

    public void showRoleSelection() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RoleSelectionView.fxml"));
            RoleSelectionViewController controller = new RoleSelectionViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load RoleSelectionView.fxml", e);
        }
    }

    public Optional<char[]> requestTokenPin() {
        Dialog<String> pinDialog = new Dialog<>();

        pinDialog.setTitle("Token PIN");
        pinDialog.setHeaderText("Enter your 6-digit PIN");

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("6-digit PIN");
        pinField.setMaxWidth(100);

        pinField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d{0,6}")) {
                pinField.setText(oldValue);
            }
        });

        VBox content = new VBox(6, pinField);
        content.setAlignment(Pos.CENTER);

        pinDialog.getDialogPane().setContent(content);

        ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);

        pinDialog.getDialogPane().getButtonTypes().setAll(confirmButton, ButtonType.CANCEL);

        Node confirmButtonNode = pinDialog.getDialogPane().lookupButton(confirmButton);
        confirmButtonNode.setDisable(true);

        pinField.textProperty().addListener((observable, oldValue, newValue) -> {
            confirmButtonNode.setDisable(!newValue.matches("\\d{6}"));
        });

        pinDialog.setResultConverter(button -> {
            if (button == confirmButton) {
                return pinField.getText();
            }
            return null;
        });

        return pinDialog.showAndWait().map(String::toCharArray);
    }

    public void showDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DashboardView.fxml"));
            DashboardViewController controller = new DashboardViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load DashboardView.fxml", e);
        }
    }

    public void showEncryptionView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FileEncryptionView.fxml"));
            FileEncryptionViewController controller = new FileEncryptionViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load FileEncryptionView.fxml", e);
        }
    }

    public void showDecryptionView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FileDecryptionView.fxml"));
            FileDecryptionViewController controller = new FileDecryptionViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load FileDecryptionView.fxml", e);
        }
    }

    public void showAuditLogs() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AuditLogView.fxml"));
            AuditLogViewController controller = new AuditLogViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load AuditLogView.fxml", e);
        }
    }

    public void showAdminUsers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AdminView.fxml"));
            AdminViewController controller = new AdminViewController(appController, this);
            loader.setController(controller);
            Parent root = loader.load();

            setScene(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load AdminView.fxml", e);
        }
    }
    
    public void logout() {
        appController.logout();
        selectedRole = null;
        showLogin();
    }

    private void setScene(Parent root) {
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
    }

    public Role getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(Role selectedRole) {
        this.selectedRole = selectedRole;
    }

}
