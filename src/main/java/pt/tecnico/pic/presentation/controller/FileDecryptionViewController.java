package pt.tecnico.pic.presentation.controller;

import java.io.File;
import java.util.Locale;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
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
        fileChooser.setInitialFileName(suggestedName);

        if (selectedInputFile != null && selectedInputFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(selectedInputFile.getParentFile());
        } else {
            fileChooser.setInitialDirectory(FileUtils.getDefaultDirectory());
        }

        File outputFile = fileChooser.showSaveDialog(stage);
        if (outputFile == null) {
            showNeutral("Decryption cancelled.");
            return;
        }

        showNeutral("Decrypting...");
        decryptButton.setDisable(true);

        File inputFile = selectedInputFile;
        Task<CryptoResult> decryptionTask = new Task<>() {
            @Override
            protected CryptoResult call() {
                return appController.decryptFile(
                        inputFile.getAbsolutePath(),
                        outputFile.getAbsolutePath()
                );
            }
        };

        decryptionTask.setOnSucceeded(event -> {
            showResult(decryptionTask.getValue());
            decryptButton.setDisable(selectedInputFile == null);
        });
        decryptionTask.setOnFailed(event -> {
            showError("Decryption failed.");
            decryptButton.setDisable(selectedInputFile == null);
        });

        Thread worker = new Thread(decryptionTask, "pic-file-decryption");
        worker.setDaemon(true);
        worker.start();
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
        if (!isEncryptedFile(file)) {
            showError("Only .enc files are supported.");
            clearSelection();
            return;
        }
        this.selectedInputFile = file;
        selectedFileLabel.setText("Selected file: " + file.getName());
        sizeLabel.setText("Size: " + FileUtils.formatSize(file.length()));
        showNeutral("Ready to start decrypting");
        decryptButton.setDisable(false);
    }

    private void setupDragAndDrop() {
        dropArea.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (event.getGestureSource() != dropArea
                    && db.hasFiles()
                    && db.getFiles().size() == 1
                    && isEncryptedFile(db.getFiles().get(0))) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        dropArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles() && db.getFiles().size() == 1) {
                File file = db.getFiles().get(0);
                if (isEncryptedFile(file)) {
                    processSelectedFile(file);
                    success = true;
                } else {
                    showError("Only .enc files are supported.");
                    clearSelection();
                }
            } else if (db.hasFiles()) {
                showError("Select exactly one encrypted file.");
                clearSelection();
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
            showSuccess("Saved as: " + finalName);
            showSuccessPopup("Decryption complete", "File decrypted successfully.", finalName);
        } else {
            showError(result.getMessage());
        }
    }

    private void showError(String message) {
        String safeMessage = message == null || message.isBlank() ? "Unknown error." : message;
        statusLabel.setText("Error: " + safeMessage);
        statusLabel.setStyle("-fx-text-fill: #991b1b;");
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #166534;");
    }

    private void showNeutral(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #6b7280;");
    }

    private void showSuccessPopup(String title, String header, String fileName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText("Saved as: " + fileName);
        if (statusLabel.getScene() != null) {
            alert.initOwner(statusLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static boolean isEncryptedFile(File file) {
        return file != null
                && file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".enc");
    }

    public void clearSelection() {
        this.selectedInputFile = null;
        selectedFileLabel.setText("Selected file: -");
        sizeLabel.setText("Size: -");
        decryptButton.setDisable(true);
    }

}
