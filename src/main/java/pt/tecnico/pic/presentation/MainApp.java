package pt.tecnico.pic.presentation;

import javafx.application.Application;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;

public class MainApp extends Application {
    private static final String WINDOW_TITLE = "PIC - Cifra de Ficheiros";

    private AppController appController;
    private SceneManager sceneManager;

    /*@Override
    public void start(Stage stage) {
        appController = new AppController();
        sceneManager = new SceneManager(stage, appController);

        Label placeholderLabel = new Label("Cifra de Ficheiros - Placeholder");
        placeholderLabel.getStyleClass().add("placeholder-label");

        VBox root = new VBox(placeholderLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, 640, 360);
        String stylesheet = getClass().getResource("/css/application.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        stage.setTitle(WINDOW_TITLE);
        stage.setScene(scene);
        stage.show();
    }*/

    @Override
    public void start(Stage stage) {
        AccountStore accountStore = new AccountStore();
        PasswordService passwordService = new PasswordService();
        AccountService accountService = new AccountService(accountStore, passwordService);

        /*AuditService auditService = new NoOpAuditService();
        JavaCryptoService javaCryptoService = new JavaCryptoService();
        FileCryptoService fileCryptoService = new FileCryptoService(javaCryptoService);

        appController = new AppController(
            accountService,
            fileCryptoService,
            auditService
        );
        */
        
        appController = new AppController();

        sceneManager = new SceneManager(stage, appController);
        stage.setTitle("PIC - Cifra de Ficheiros");
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
