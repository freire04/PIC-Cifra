package pt.tecnico.pic.presentation.controller;

import java.io.File;

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
            showNeutral("Encryption cancelled.");
            return;
        }

        if (!outputFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".enc")) {
            outputFile = new File(outputFile.getParent(), outputFile.getName() + ".enc");
        }

        if (outputFile.exists()) {
            showError("Output file already exists.");
            return;
        }

        showNeutral("Encrypting...");
        encryptButton.setDisable(true);

        File inputFile = selectedInputFile;
        File finalOutputFile = outputFile;
        Task<CryptoResult> encryptionTask = new Task<>() {
            @Override
            protected CryptoResult call() {
                return appController.encryptFile(
                        inputFile.getAbsolutePath(),
                        finalOutputFile.getAbsolutePath()
                );
            }
        };

        encryptionTask.setOnSucceeded(event -> {
            showResult(encryptionTask.getValue());
            encryptButton.setDisable(selectedInputFile == null);
        });
        encryptionTask.setOnFailed(event -> {
            showError("Encryption failed.");
            encryptButton.setDisable(selectedInputFile == null);
        });

        Thread worker = new Thread(encryptionTask, "pic-file-encryption");
        worker.setDaemon(true);
        worker.start();
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
        showNeutral("Ready to start encrypting");
        encryptButton.setDisable(false);
    }

    private void setupDragAndDrop() {
        dropArea.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (event.getGestureSource() != dropArea && db.hasFiles() && db.getFiles().size() == 1) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        dropArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles() && db.getFiles().size() == 1) {
                processSelectedFile(db.getFiles().get(0));
                success = true;
            } else if (db.hasFiles()) {
                showError("Select exactly one file.");
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
            showSuccessPopup("Encryption complete", "File encrypted successfully.", finalName);
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

    public void clearSelection() {
        this.selectedInputFile = null;
        selectedFileLabel.setText("Selected file: -");
        sizeLabel.setText("Size: -");
        encryptButton.setDisable(true);
    }

}
