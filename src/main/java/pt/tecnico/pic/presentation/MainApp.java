package pt.tecnico.pic.presentation;

import javafx.application.Application;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
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

        AuditService auditService = new AuditService();

        // JavaCryptoService javaCryptoService = new JavaCryptoService();
        // JavaCryptoService é para ser criado nesta classe assim que ele for definido.
        // Falta injetar o javaCryptoService no fileCryptoService, assumo que isso seja o S01-09.
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);
        
        // Falta injetar o accountService no appController, assumo que isso seja o S01-10 (ou parecido).
        appController = new AppController(auditService, fileCryptoService);
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
}
