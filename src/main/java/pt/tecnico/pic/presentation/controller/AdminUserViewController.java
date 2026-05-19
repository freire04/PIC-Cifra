package pt.tecnico.pic.presentation.controller;

import java.util.List;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.AccountSummary;
import pt.tecnico.pic.presentation.SceneManager;

public class AdminUserViewController {
    private final AppController appController;
    private final SceneManager sceneManager;
    private AccountSummary selectedAccount;

    public AdminUserViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    public void initialize() {}

    public void loadUsers() {}

    public void onCreateAccount() {}

    public void onUpdateRoles() {}

    public void onResetPassword() {}

    public void onDisableAccount() {}

    public void onEnableAccount() {}

    public void showUsers(List<AccountSummary> users) {}

    public void showResult(AccountResult result) {}

    public void showError(String message) {}

    public AccountSummary getSelectedAccount() {
        return selectedAccount;
    }

    public void setSelectedAccount(AccountSummary selectedAccount) {
        this.selectedAccount = selectedAccount;
    }

}
