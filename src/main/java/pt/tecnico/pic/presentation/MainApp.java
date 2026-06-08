package pt.tecnico.pic.presentation;

import javafx.application.Application;
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
        accountService.bootstrapInitialAdmin()
                .ifPresent(result -> printInitialAdminCredentials(result, passwordService));

        PKCS11Service pkcs11Service = new PKCS11Service();
        AuditService auditService = new AuditService();
        FileCryptoService fileCryptoService = new FileCryptoService(pkcs11Service, auditService);

        appController = new AppController(accountService, auditService, fileCryptoService);
        sceneManager = new SceneManager(stage, appController);

        stage.setTitle(WINDOW_TITLE);
        sceneManager.showLogin();
        stage.show();
    }

    @Override
    public void stop() {
        // fechar recursos, guardar estado, etc. se necessário
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void printInitialAdminCredentials(
            AccountCreationResult result,
            PasswordService passwordService
    ) {
        char[] temporaryPassword = result.getTemporaryPassword();

        try {
            System.out.println("Initial ADMIN account created.");
            System.out.println("Username: " + result.getUsername());
            System.out.print("Temporary password: ");
            System.out.println(temporaryPassword);
            System.out.println("Change this password after the first login.");
        } finally {
            passwordService.clear(temporaryPassword);
        }
    }
}
