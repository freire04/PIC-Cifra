package pt.tecnico.pic.presentation;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;

public class MainApp extends Application {
    private static final String WINDOW_TITLE = "PIC - Cifra de Ficheiros";

    private AppController appController;

    @Override
    public void start(Stage stage) {
        appController = new AppController();

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
    }

    public static void main(String[] args) {
        launch(args);
    }
}
