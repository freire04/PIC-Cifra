package pt.tecnico.pic.presentation.controller;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.presentation.SceneManager;

public class MainMenuViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    public MainMenuViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    public void initialize() {}

    public void onEncryptSelected() {}

    public void onDecryptSelected() {}

    public void onAuditLogsSelected() {}

    public void onAdminUsersSelected() {}

    public void onLogoutClicked() {}

}
