package pt.tecnico.pic.presentation;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.controller.AdminUserViewController;
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

// TODO: call appController.logout() when session handling is implemented.

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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChangePasswordView.fxml"));
            ChangePasswordViewController controller = new ChangePasswordViewController(appController, this);
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

        Label hintLabel = new Label("Hint: Use 123456 for testing");
        hintLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        VBox content = new VBox(6, hintLabel, pinField);
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
        showFileCryptoPlaceholder(true);
    }

    public void showDecryptionView() {
        showFileCryptoPlaceholder(false);
    }

    public void showAuditLogs() {
        AuditLogViewController controller = new AuditLogViewController(appController, this);

        Label title = new Label("Audit Logs View");

        Button changeRoleButton = new Button("Change Role");
        changeRoleButton.setOnAction(event -> showRoleSelection());

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        HBox topRightButtons = new HBox(12, changeRoleButton, logoutButton);
        topRightButtons.setAlignment(Pos.TOP_RIGHT);

        VBox centerContent = new VBox(title);
        centerContent.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();

        root.setTop(topRightButtons);
        root.setCenter(centerContent);

        BorderPane.setAlignment(topRightButtons, Pos.TOP_RIGHT);

        setScene(root);
    }

    public void showAdminUsers() {
        AdminUserViewController controller = new AdminUserViewController(appController, this);
        
        Label title = new Label("Admin Users View");

        Button changeRoleButton = new Button("Change Role");
        changeRoleButton.setOnAction(event -> showRoleSelection());

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(event -> logout());

        HBox topRightButtons = new HBox(12, changeRoleButton, logoutButton);
        topRightButtons.setAlignment(Pos.TOP_RIGHT);

        VBox centerContent = new VBox(title);
        centerContent.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();

        root.setTop(topRightButtons);
        root.setCenter(centerContent);

        BorderPane.setAlignment(topRightButtons, Pos.TOP_RIGHT);

        setScene(root);
    }
    
    public void logout() {
        // TODO (S1-10): call appController.logout().
        // appController.logout();
        showLogin();
    }

    private void showFileCryptoPlaceholder(boolean encryptionMode) {

        if (encryptionMode) {
            FileEncryptionViewController controller = new FileEncryptionViewController(appController, this);
        } else {
            FileDecryptionViewController controller = new FileDecryptionViewController(appController, this);
        }

        String dropText = encryptionMode
                ? "Drop file to encrypt here"
                : "Drop file to decrypt here";

        String actionText = encryptionMode
                ? "Encrypt"
                : "Decrypt";

        Button encryptTab = new Button("Encrypt File");
        encryptTab.setOnAction(event -> showEncryptionView());
        encryptTab.setDisable(encryptionMode);

        Button decryptTab = new Button("Decrypt File");
        decryptTab.setOnAction(event -> showDecryptionView());
        decryptTab.setDisable(!encryptionMode);

        HBox tabs = new HBox(8, encryptTab, decryptTab);
        tabs.setAlignment(Pos.CENTER_LEFT);

        Label uploadIcon = new Label("⇧");
        uploadIcon.setStyle("-fx-font-size: 36px;");

        Label dropLabel = new Label(dropText + " or browse");
        Label supportedLabel = new Label("Any file type supported");

        VBox dropContent = new VBox(8, uploadIcon, dropLabel, supportedLabel);
        dropContent.setAlignment(Pos.CENTER);

        VBox dropArea = new VBox(dropContent);
        dropArea.setAlignment(Pos.CENTER);
        dropArea.setPrefSize(560, 220);
        dropArea.setMinSize(560, 220);
        dropArea.setMaxSize(560, 220);

        dropArea.setStyle(
                "-fx-border-color: #9ca3af;" +
                "-fx-border-style: dashed;" +
                "-fx-border-width: 2;" +
                "-fx-background-color: #f9fafb;"
        );

        Label selectedFileLabel = new Label("Selected file: -");
        Label sizeLabel = new Label("Size: -");

        HBox fileInfo = new HBox(260, selectedFileLabel, sizeLabel);
        fileInfo.setAlignment(Pos.CENTER);

        Button actionButton = new Button(actionText);
        actionButton.setDisable(true);

        Button backButton = new Button("Back");
        backButton.setOnAction(event -> showDashboard());

        HBox bottomButtons = new HBox(12, actionButton, backButton);
        bottomButtons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(20, tabs, dropArea, fileInfo, bottomButtons);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new javafx.geometry.Insets(24));

        setScene(root);
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
