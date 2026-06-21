package pt.tecnico.pic.presentation.controller;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.presentation.SceneManager;
import pt.tecnico.pic.util.FileUtils;

public class FileDecryptionViewController {

    private final AppController appController;
    private final SceneManager sceneManager;
    private File selectedInputFile;

    @FXML private VBox dropArea;
    @FXML private Label selectedFileLabel;
    @FXML private Label sizeLabel;
    @FXML private Button decryptButton;
    @FXML private Label statusLabel;

    public FileDecryptionViewController(AppController appController, SceneManager sceneManager) {
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
        fileChooser.setTitle("Select an encrypted file");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Encrypted Files (*.enc)", "*.enc")
        );

        Stage stage = (Stage) dropArea.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            processSelectedFile(file);
        }
    }

    @FXML
    public void onDecryptClicked() {
        if (selectedInputFile == null) {
            showError("No input file selected.");
            return;
        }

        Stage stage = (Stage) dropArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save decrypted file");
        String suggestedName = FileUtils.suggestDecryptedFileName(selectedInputFile);
        int lastDot = suggestedName.lastIndexOf('.');
        String cleanName = (lastDot == -1) ? suggestedName : suggestedName.substring(0, lastDot);
        fileChooser.setInitialFileName(cleanName);
        
        if (selectedInputFile != null && selectedInputFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(selectedInputFile.getParentFile());
        } else {
            fileChooser.setInitialDirectory(FileUtils.getDefaultDirectory());
        }

        File outputFile = fileChooser.showSaveDialog(stage);
        if (outputFile == null) {
            statusLabel.setText("Decryption cancelled.");
            return;
        }
        
        statusLabel.setText("Decrypting...");
        decryptButton.setDisable(true);

        CryptoResult result = appController.decryptFile(
            selectedInputFile.getAbsolutePath(),
            outputFile.getAbsolutePath()
        );

        showResult(result);
        decryptButton.setDisable(false);
    }

    @FXML
    public void onEncryptSelected() {
        sceneManager.showEncryptionView();
    }

    @FXML
    public void onBackClicked() {
        sceneManager.showDashboard();
    }

    // --- LÓGICA DE SUPORTE À UI ---

    private void processSelectedFile(File file) {
        // Proteção extra se o ficheiro não for .enc; em principio nem deve ser usado.
        if (!file.getName().endsWith(".enc")) {
            showError("Only .enc files are supported.");
            clearSelection();
            return;
        }
        this.selectedInputFile = file;
        selectedFileLabel.setText("Selected file: " + file.getName());
        sizeLabel.setText("Size: " + FileUtils.formatSize(file.length()));
        statusLabel.setText("Ready to start decrypting");
        decryptButton.setDisable(false);
    }

    private void setupDragAndDrop() {
        dropArea.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            // MODIFICAÇÃO: Só aceita o arrastar se o ficheiro terminar em .enc
            if (event.getGestureSource() != dropArea && db.hasFiles() && !db.getFiles().isEmpty()) {
                if (db.getFiles().get(0).getName().endsWith(".enc")) {
                    event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                }
            }
            event.consume();
        });

        dropArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                if (file.getName().endsWith(".enc")) {
                    processSelectedFile(file);
                    success = true;
                } else {
                    showError("Only .enc files are supported.");
                }
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
        decryptButton.setDisable(true);
    }

}