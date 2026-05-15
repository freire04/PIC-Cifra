package pt.tecnico.pic.presentation.controller;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.presentation.SceneManager;

public class LoginViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    public LoginViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    private void initialize() {}

    private void onLoginClicked() {}

    private void showError(String message) {}

    private void clearFields() {}

}
