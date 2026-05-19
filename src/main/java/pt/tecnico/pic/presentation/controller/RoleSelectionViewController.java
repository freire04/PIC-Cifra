package pt.tecnico.pic.presentation.controller;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.SceneManager;

public class RoleSelectionViewController {
    private final AppController appController;
    private final SceneManager sceneManager;

    public RoleSelectionViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    public void initialize() {}

    public void loadAvailableRoles() {}

    public void onRoleSelected(Role role) {}

    public void onTokenPinSubmitted() {}

    public void showPinPrompt() {}

    public void showError(String message) {}

}
