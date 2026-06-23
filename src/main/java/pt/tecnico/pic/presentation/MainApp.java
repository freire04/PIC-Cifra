package pt.tecnico.pic.presentation;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.crypto.PKCS11Service;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;

public class MainApp extends Application {
    private static final String WINDOW_TITLE = "PIC - Cifra de Ficheiros";

    private AppController appController;
    private SceneManager sceneManager;

    @Override
    public void start(Stage stage) {
        AccountStore accountStore = new AccountStore();
        PasswordService passwordService = new PasswordService();
        AccountService accountService = new AccountService(accountStore, passwordService);
        AccountCreationResult initialAdmin = accountService.bootstrapInitialAdmin().orElse(null);

        PKCS11Service pkcs11Service = new PKCS11Service();
        AuditService auditService = new AuditService();
        FileCryptoService fileCryptoService = new FileCryptoService(pkcs11Service, auditService);

        appController = new AppController(accountService, auditService, fileCryptoService);
        sceneManager = new SceneManager(stage, appController);

        stage.setTitle(WINDOW_TITLE);
        sceneManager.showLogin();
        stage.show();

        if (initialAdmin != null) {
            showInitialAdminCredentials(stage, initialAdmin, passwordService);
        }
    }

    @Override
    public void stop() {
        if (appController != null && appController.hasActiveSession()) {
            appController.logout();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void showInitialAdminCredentials(
            Stage owner,
            AccountCreationResult result,
            PasswordService passwordService
    ) {
        char[] temporaryPassword = result.getTemporaryPassword();
        String temporaryPasswordText = temporaryPassword == null ? "" : String.valueOf(temporaryPassword);

        try {
            TextArea passwordArea = new TextArea(temporaryPasswordText);
            passwordArea.setEditable(false);
            passwordArea.setWrapText(false);
            passwordArea.setPrefRowCount(1);
            passwordArea.setPrefColumnCount(28);

            VBox content = new VBox(
                    8,
                    new Label("Username: " + result.getUsername()),
                    new Label("Temporary password:"),
                    passwordArea,
                    new Label("Change this password after the first login.")
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Initial ADMIN account");
            alert.setHeaderText("Initial ADMIN account created");
            alert.getDialogPane().setContent(content);
            alert.showAndWait();
        } finally {
            passwordService.clear(temporaryPassword);
            result.clearTemporaryPassword();
        }
    }
}
