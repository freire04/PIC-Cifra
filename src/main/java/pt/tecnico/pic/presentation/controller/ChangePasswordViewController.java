package pt.tecnico.pic.presentation.controller;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.presentation.SceneManager;

public class ChangePasswordViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    public ChangePasswordViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    public void initialize() {}

    public void onChangePasswordClicked(String oldPassword, String newPassword, String confirmPassword) {}

    public void onCancelClicked() {}

    public void validatePasswords() {}

    public void showError(String message) {}

    public void clearFields() {}
}
