package pt.tecnico.pic.presentation.controller;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.presentation.SceneManager;
import pt.tecnico.pic.util.FileUtils;

public class FileEncryptionViewController {

    private final AppController appController;
    private final SceneManager sceneManager;
    private File selectedInputFile;

    @FXML private VBox dropArea;
    @FXML private Label selectedFileLabel;
    @FXML private Label sizeLabel;
    @FXML private Button encryptButton;
    @FXML private Label statusLabel;

    public FileEncryptionViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        dropArea.setFocusTraversable(true);
        dropArea.setOnMouseClicked(event -> handleBrowseInput());
        setupDragAndDrop();
    }

    // --- AÇÕES DO UTILIZADOR (Event Handlers) ---

    private void handleBrowseInput() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a file");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Files", "*.*"));

        Stage stage = (Stage) dropArea.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            processSelectedFile(file);
        }
    }

    @FXML
    public void onEncryptClicked() {
        if (selectedInputFile == null) {
            showError("No input file selected.");
            return;
        }

        Stage stage = (Stage) dropArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save encrypted file");
        fileChooser.setInitialFileName(FileUtils.suggestEncryptedFileName(selectedInputFile));
        if (selectedInputFile != null && selectedInputFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(selectedInputFile.getParentFile());
        } else {
            fileChooser.setInitialDirectory(FileUtils.getDefaultDirectory());
        }

        File outputFile = fileChooser.showSaveDialog(stage);
        if (outputFile == null) {
            statusLabel.setText("Encryption cancelled.");
            return;
        }

        if (!outputFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".enc")) {
            outputFile = new File(outputFile.getParent(), outputFile.getName() + ".enc");
        }
        
        statusLabel.setText("Encrypting...");
        encryptButton.setDisable(true);

        CryptoResult result = appController.encryptFile(
            selectedInputFile.getAbsolutePath(),
            outputFile.getAbsolutePath()
        );

        showResult(result);
        encryptButton.setDisable(false);
    }

    @FXML
    public void onDecryptSelected() {
        sceneManager.showDecryptionView();
    }

    @FXML
    public void onBackClicked() {
        sceneManager.showDashboard();
    }

    // --- LÓGICA DE SUPORTE À UI ---

    private void processSelectedFile(File file) {
        this.selectedInputFile = file;
        selectedFileLabel.setText("Selected file: " + file.getName());
        sizeLabel.setText("Size: " + FileUtils.formatSize(file.length()));
        statusLabel.setText("Ready to start encrypting");
        encryptButton.setDisable(false);
    }

    private void setupDragAndDrop() {
        dropArea.setOnDragOver(event -> {
            if (event.getGestureSource() != dropArea && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        dropArea.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                processSelectedFile(db.getFiles().get(0));
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void showResult(CryptoResult result) {
        if (result == null) {
            showError("No result returned.");
            return;
        }

        if (result.getResult() == OperationResult.SUCCESS) {
            String finalName = new File(result.getOutputFilePath()).getName();
            statusLabel.setText("Saved as: " + finalName);
        } else {
            showError(result.getMessage());
        }
    }

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
    }

    public void clearSelection() {
        this.selectedInputFile = null;
        selectedFileLabel.setText("Selected file: -");
        sizeLabel.setText("Size: -");
        encryptButton.setDisable(true);
    }

}
