package pt.tecnico.pic.presentation.controller;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.AccountStatusFilter;
import pt.tecnico.pic.dto.AccountSummary;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.presentation.SceneManager;

public class AdminViewController {
    private final AppController appController;
    private final SceneManager sceneManager;
    private AccountSummary selectedAccount;

    @FXML
    private CheckBox userFilterCheckBox;
    @FXML
    private CheckBox auditorFilterCheckBox;
    @FXML
    private CheckBox adminFilterCheckBox;
    @FXML
    private TextField searchField;
    @FXML
    private Label accountCountLabel;
    @FXML
    private FlowPane accountsContainer;
    @FXML
    private Label resultLabel;
    @FXML
    private CheckBox activeFilterCheckBox;
    @FXML
    private CheckBox disabledFilterCheckBox;

    public AdminViewController(AppController appController, SceneManager sceneManager) {
        this.appController = appController;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        resultLabel.setText("");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadUsers());
        userFilterCheckBox.setOnAction(event -> loadUsers());
        auditorFilterCheckBox.setOnAction(event -> loadUsers());
        adminFilterCheckBox.setOnAction(event -> loadUsers());
        activeFilterCheckBox.setOnAction(event -> loadUsers());
        disabledFilterCheckBox.setOnAction(event -> loadUsers());

        loadUsers();
    }

    public void loadUsers() {
        List<AccountSummary> users = appController.searchAccounts(buildFilter())
                .stream()
                .sorted(Comparator.comparing(AccountSummary::getUsername))
                .toList();

        showUsers(users);
    }

    private AccountFilter buildFilter() {
        return new AccountFilter(
                searchField.getText(),
                selectedRolesFromUI(),
                buildStatusFromCheckboxes()
        );
    }

    private AccountStatusFilter buildStatusFromCheckboxes() {
        boolean active = activeFilterCheckBox.isSelected();
        boolean disabled = disabledFilterCheckBox.isSelected();

        if (active && disabled) return AccountStatusFilter.ALL;
        if (active) return AccountStatusFilter.ACTIVE;
        if (disabled) return AccountStatusFilter.DISABLED;

        return AccountStatusFilter.NONE;
    }

    private Set<Role> selectedRolesFromUI() {
        return selectedRoles(
                userFilterCheckBox,
                auditorFilterCheckBox,
                adminFilterCheckBox
        );
    }

    @FXML
    public void onCreateAccount() {
        showCreateDialog();
    }

    @FXML
    public void onBackClicked() {
        sceneManager.showDashboard();
    }

    public void showUsers(List<AccountSummary> accounts) {
        accountsContainer.getChildren().clear();
        accountCountLabel.setText(accounts.size() + " shown");

        for (AccountSummary account : accounts) {
            accountsContainer.getChildren().add(createUserCard(account));
        }
    }

    public void showResult(AccountResult result) {
        if (result.getResult() == OperationResult.SUCCESS) {
            showSuccess(result.getMessage());
        } else {
            showError(result.getMessage());
        }
    }

    public void showError(String message) {
        resultLabel.setText(message);
        resultLabel.setStyle("-fx-text-fill: red;");
    }

    public AccountSummary getSelectedAccount() {
        return selectedAccount;
    }

    public void setSelectedAccount(AccountSummary selectedAccount) {
        this.selectedAccount = selectedAccount;
        if (selectedAccount != null) {
            showEditDialog(selectedAccount);
        }
    }

    private void createAccount(String username, Set<Role> roles) {
        AccountCreationResult result = appController.createAccount(
                new CreateAccountRequest(username, roles)
        );
        char[] temporaryPassword = result.getTemporaryPassword();

        try {
            if (result.getResult() == OperationResult.SUCCESS && temporaryPassword != null) {
                showSuccess(result.getMessage());
                showTemporaryPassword(result.getUsername(), temporaryPassword);
            } else {
                showError(result.getMessage());
            }
            loadUsers();
        } finally {
            result.clearTemporaryPassword();
            if (temporaryPassword != null) {
                Arrays.fill(temporaryPassword, '\0');
            }
        }
    }

    private VBox createUserCard(AccountSummary account) {
        Label nameLabel = new Label(account.getUsername());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label rolesLabel = new Label(formatRoles(account));
        rolesLabel.setStyle("-fx-text-fill: gray;");

        Label statusLabel = new Label(account.isActive() ? "Active" : "Disabled");
        statusLabel.setStyle(account.isActive() ? "-fx-text-fill: #166534;" : "-fx-text-fill: #991b1b;");

        Button editButton = new Button("Edit");
        editButton.setOnAction(event -> setSelectedAccount(account));

        VBox card = new VBox(6, nameLabel, rolesLabel, statusLabel, editButton);
        card.setPadding(new Insets(12));
        card.setPrefSize(145, 115);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(cardStyle(account));
        card.setOnMouseClicked(event -> setSelectedAccount(account));

        return card;
    }

    private void showCreateDialog() {
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        RoleSelector roleSelector = new RoleSelector(Set.of());

        Dialog<AccountFormData> dialog = new Dialog<>();
        dialog.setTitle("Create User");
        dialog.setHeaderText("Create a new user account");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(createButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(12, usernameField, roleSelector.root()));

        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        usernameField.textProperty().addListener((observable, oldValue, newValue) ->
                createButton.setDisable(newValue == null || newValue.isBlank())
        );

        dialog.setResultConverter(button -> {
            if (button == createButtonType) {
                return new AccountFormData(usernameField.getText(), roleSelector.selectedRoles());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(formData -> createAccount(formData.username(), formData.roles()));
    }

    private void showEditDialog(AccountSummary account) {
        Label usernameLabel = new Label(account.getUsername());
        usernameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        RoleSelector roleSelector = new RoleSelector(account.getRoles());

        ButtonType saveButtonType = new ButtonType("Save Roles", ButtonBar.ButtonData.OK_DONE);
        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OTHER);
        ButtonType toggleActiveButtonType = new ButtonType(
                account.isActive() ? "Disable User" : "Enable User",
                ButtonBar.ButtonData.OTHER
        );

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Manage selected user");
        dialog.getDialogPane().getButtonTypes().setAll(
                saveButtonType,
                resetButtonType,
                toggleActiveButtonType,
                ButtonType.CANCEL
        );
        dialog.getDialogPane().setContent(new VBox(12, usernameLabel, roleSelector.root()));

        dialog.showAndWait().ifPresent(button -> {
            if (button == saveButtonType) {
                AccountResult result = appController.updateUserRoles(account.getAccountId(), roleSelector.selectedRoles());
                showResult(result);
                loadUsers();
            } else if (button == resetButtonType) {
                resetPassword(account);
            } else if (button == toggleActiveButtonType) {
                toggleActive(account);
            }
        });
    }

    private Set<Role> selectedRoles(CheckBox userRoleCheckBox, CheckBox auditorRoleCheckBox, CheckBox adminRoleCheckBox) {
        return java.util.stream.Stream.of(
                        userRoleCheckBox.isSelected() ? Role.USER : null,
                        auditorRoleCheckBox.isSelected() ? Role.AUDITOR : null,
                        adminRoleCheckBox.isSelected() ? Role.ADMIN : null
                )
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void resetPassword(AccountSummary user) {
        PasswordResult result = appController.resetPassword(user.getAccountId());
        char[] temporaryPassword = result.getTemporaryPassword();

        try {
            if (result.getResult() == OperationResult.SUCCESS && temporaryPassword != null) {
                showSuccess(result.getMessage());
                showTemporaryPassword(user.getUsername(), temporaryPassword);
            } else {
                showError(result.getMessage());
            }
            loadUsers();
        } finally {
            if (temporaryPassword != null) {
                Arrays.fill(temporaryPassword, '\0');
            }
        }
    }

    private void toggleActive(AccountSummary account) {
        AccountResult result = account.isActive()
                ? appController.disableAccount(account.getAccountId())
                : appController.enableAccount(account.getAccountId());

        showResult(result);
        loadUsers();
    }

    private void showSuccess(String message) {
        resultLabel.setText(message);
        resultLabel.setStyle("-fx-text-fill: green;");
    }

    private void showTemporaryPassword(String username, char[] temporaryPassword) {
        TextField passwordField = new TextField(new String(temporaryPassword));
        passwordField.setEditable(false);
        passwordField.setFocusTraversable(true);
        passwordField.setPrefWidth(260);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Temporary Password");
        dialog.setHeaderText("Temporary password for " + username);
        dialog.getDialogPane().setContent(new VBox(8, new Label("Select and copy this password:"), passwordField));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        dialog.setOnShown(event -> passwordField.selectAll());
        dialog.showAndWait();
    }

    private String formatRoles(AccountSummary user) {
        return user.getRoles()
                .stream()
                .map(Role::name)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String cardStyle(AccountSummary user) {
        String borderColor = user.equals(selectedAccount) ? "#2563eb" : "#d1d5db";
        return "-fx-background-color: white;"
                + "-fx-border-color: " + borderColor + ";"
                + "-fx-border-radius: 6;"
                + "-fx-background-radius: 6;";
    }

    private record AccountFormData(String username, Set<Role> roles) {}

    private final class RoleSelector {
        private final CheckBox userRoleCheckBox = new CheckBox("USER");
        private final CheckBox auditorRoleCheckBox = new CheckBox("AUDITOR");
        private final CheckBox adminRoleCheckBox = new CheckBox("ADMIN");

        private RoleSelector(Set<Role> selectedRoles) {
            userRoleCheckBox.setSelected(selectedRoles.contains(Role.USER));
            auditorRoleCheckBox.setSelected(selectedRoles.contains(Role.AUDITOR));
            adminRoleCheckBox.setSelected(selectedRoles.contains(Role.ADMIN));
        }

        private HBox root() {
            HBox root = new HBox(12, userRoleCheckBox, auditorRoleCheckBox, adminRoleCheckBox);
            root.setAlignment(Pos.CENTER_LEFT);
            return root;
        }

        private Set<Role> selectedRoles() {
            return AdminViewController.this.selectedRoles(
                    userRoleCheckBox,
                    auditorRoleCheckBox,
                    adminRoleCheckBox
            );
        }
    }

}
