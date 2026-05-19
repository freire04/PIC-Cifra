package pt.tecnico.pic.presentation.controller;

import java.io.File;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.presentation.SceneManager;

public class FileDecryptionViewController {
    private final AppController appController;
    private final SceneManager sceneManager;
    private File selectedInputFile;
    private File selectedOutputFile;

    public FileDecryptionViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    public void initialize() {}

    public void onBrowseInput() {}

    public void onBrowseOutput() {}

    public void onFileDropped(File file) {}

    public void onDecryptClicked() {}

    public void showResult(CryptoResult result) {}

    public void showError(String message) {}

    public void clearSelection() {}

    public File getSelectedInputFile() {
        return selectedInputFile;
    }

    public File getSelectedOutputFile() {
        return selectedOutputFile;
    }

    public void setSelectedInputFile(File selectedInputFile) {
        this.selectedInputFile = selectedInputFile;
    }

    public void setSelectedOutputFile(File selectedOutputFile) {
        this.selectedOutputFile = selectedOutputFile;
    }

}
