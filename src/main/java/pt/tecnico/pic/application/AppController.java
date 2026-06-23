package pt.tecnico.pic.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import pt.tecnico.pic.domain.Account;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.Session;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.AccountSummary;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;

/**
 * Central application facade.
 *
 * ViewControllers should use this class as the only entry point into the
 * application layer.
 */
public class AppController {

    private final AccountService accountService;
    private final AuditService auditService;
    private final FileCryptoService fileCryptoService;

    private Session currentSession;
    private boolean currentMustChangePassword;
    private boolean viewLogsLoggedForSelectedRole;

    public AppController() {
        this(new AccountService(), new AuditService());
    }

    public AppController(AccountService accountService, AuditService auditService) {
        this(accountService, auditService, new FileCryptoService(auditService));
    }

    public AppController(AccountService accountService, AuditService auditService, FileCryptoService fileCryptoService) {
        this.accountService = Objects.requireNonNull(accountService, "accountService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.fileCryptoService = Objects.requireNonNull(fileCryptoService, "fileCryptoService must not be null");

        if (this.fileCryptoService.getAuditService() != this.auditService) {
            throw new IllegalArgumentException("fileCryptoService must use the provided auditService");
        }
    }

    public LoginResult login(String username, char[] password) {
        Account account = accountService.authenticate(username, password);

        if (account == null) {
            clearSessionState();

            auditService.log(
                    null,
                    username,
                    null,
                    ActionType.LOGIN,
                    null,
                    OperationResult.FAILED,
                    "Login failed."
            );

            return new LoginResult(
                    OperationResult.FAILED,
                    "Login failed.",
                    -1,
                    username,
                    Set.of(),
                    false
            );
        }

        currentSession = new Session(account.getId(), account.getUsername(), account.getRoles());
        currentMustChangePassword = account.mustChangePassword();
        resetViewLogsAuditPolicy();

        auditService.log(
                account.getId(),
                account.getUsername(),
                null,
                ActionType.LOGIN,
                null,
                OperationResult.SUCCESS,
                "Login successful."
        );

        return new LoginResult(
                OperationResult.SUCCESS,
                "Login successful.",
                account.getId(),
                account.getUsername(),
                account.getRoles(),
                account.mustChangePassword()
        );
    }

    public OperationResult logout() {
        if (!isLoggedIn()) {
            logProtectedActionDenied(
                    ActionType.LOGOUT,
                    null,
                    "Logout failed: no authenticated user."
            );
            return OperationResult.FAILED;
        }

        Integer accountId = currentSession.getAccountId();
        String username = currentSession.getUsername();
        Role actorRole = currentSession.getSelectedRole();

        OperationResult result = OperationResult.SUCCESS;

        if (fileCryptoService.isTokenUnlocked()) {
            result = fileCryptoService.lockToken(currentUserContext());
        }

        auditService.log(
                accountId,
                username,
                actorRole,
                ActionType.LOGOUT,
                null,
                result,
                result == OperationResult.SUCCESS ? "Logout successful." : "Logout completed with token lock failure."
        );

        currentSession = null;
        currentMustChangePassword = false;
        resetViewLogsAuditPolicy();

        return result;
    }

    public Set<Role> getAvailableRoles() {
        if (!isLoggedIn() || currentMustChangePassword) {
            return Set.of();
        }

        return currentSession.getAvailableRoles();
    }

    public RoleSelectionResult selectRole(Role role, char[] tokenPin) {
        if (!isLoggedIn()) {
            logProtectedActionDenied(
                    ActionType.SELECT_ROLE,
                    null,
                    "Role selection failed: user is not logged in."
            );
            return new RoleSelectionResult(
                    OperationResult.FAILED,
                    "User is not logged in.",
                    null,
                    false
            );
        }

        if (currentMustChangePassword) {
            logProtectedActionDenied(
                    ActionType.SELECT_ROLE,
                    null,
                    "Role selection failed: password must be changed."
            );
            return new RoleSelectionResult(
                    OperationResult.FAILED,
                    "Password must be changed before selecting a role.",
                    null,
                    false
            );
        }

        if (role == null || !currentSession.getAvailableRoles().contains(role)) {
            auditService.log(
                    currentSession.getAccountId(),
                    currentSession.getUsername(),
                    currentSession.getSelectedRole(),
                    ActionType.SELECT_ROLE,
                    null,
                    OperationResult.FAILED,
                    "Role selection failed."
            );

            return new RoleSelectionResult(
                    OperationResult.FAILED,
                    "Role is not available for this account.",
                    null,
                    /*currentSession.isTokenUnlocked()*/
                    fileCryptoService.isTokenUnlocked()
            );
        }

        if (role == Role.USER) {
            OperationResult unlockResult = fileCryptoService.unlockToken(tokenPin, currentUserContext());

            if (unlockResult != OperationResult.SUCCESS) {
                //currentSession.lockToken();

                auditService.log(
                        currentSession.getAccountId(),
                        currentSession.getUsername(),
                        null,
                        ActionType.SELECT_ROLE,
                        null,
                        unlockResult,
                        "Role selection failed."
                );

                return new RoleSelectionResult(
                        unlockResult,
                        "Token unlock failed.",
                        null,
                        false
                );
            }

            currentSession.selectRole(role);
        } else {
            if (fileCryptoService.isTokenUnlocked()) {
                fileCryptoService.lockToken(currentUserContext());
            }

            currentSession.selectRole(role);
        }
        resetViewLogsAuditPolicy();

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.SELECT_ROLE,
                null,
                OperationResult.SUCCESS,
                "Role selected successfully."
        );

        return new RoleSelectionResult(
                OperationResult.SUCCESS,
                "Role selected successfully.",
                role,
                //currentSession.isTokenUnlocked()
                fileCryptoService.isTokenUnlocked()
        );
    }

    public List<AccountSummary> getUsers(AccountFilter filter) {
        if (!canManageAccounts()) return List.of();

        return accountService.searchAccounts(filter);
    }

    public AccountCreationResult createAccount(CreateAccountRequest request) {
        if (!canManageAccounts()) {
            String message = "Only ADMIN can create accounts.";
            logProtectedActionDenied(ActionType.CREATE_ACCOUNT, null, message);
            return new AccountCreationResult(
                    OperationResult.FAILED,
                    -1,
                    null,
                    null,
                    message
            );
        }

        AccountCreationResult result = accountService.createAccount(request.getUsername(), request.getRoles());

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.CREATE_ACCOUNT,
                null,
                result.getResult(),
                result.getMessage()
        );

        return result;
    }

    public AccountResult updateUserRoles(int accountId, Set<Role> roles) {
        if (!canManageAccounts()) {
            String message = "Only ADMIN can update user roles.";
            logProtectedActionDenied(ActionType.UPDATE_ROLES, null, message);
            return new AccountResult(OperationResult.FAILED, message);
        }

        AccountResult result = accountService.updateRoles(accountId, roles);

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.UPDATE_ROLES,
                null,
                result.getResult(),
                result.getMessage()
        );

        if (result.getResult() == OperationResult.SUCCESS && isCurrentAccount(accountId)) {
            refreshCurrentSession();
        }

        return result;
    }

    public PasswordResult resetPassword(int accountId) {
        if (!canManageAccounts()) {
            String message = "Only ADMIN can reset passwords.";
            logProtectedActionDenied(ActionType.RESET_PASSWORD, null, message);
            return new PasswordResult(OperationResult.FAILED, message, null);
        }

        PasswordResult result;
        if (isCurrentAccount(accountId)) {
            result = new PasswordResult(
                    OperationResult.FAILED,
                    "Use Change Password to update your own password.",
                    null
            );
        } else {
            result = accountService.resetPassword(accountId);
        }

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.RESET_PASSWORD,
                null,
                result.getResult(),
                result.getMessage()
        );

        return result;
    }

    public AccountResult disableAccount(int accountId) {
        if (!canManageAccounts()) {
            String message = "Only ADMIN can disable accounts.";
            logProtectedActionDenied(ActionType.DISABLE_ACCOUNT, null, message);
            return new AccountResult(OperationResult.FAILED, message);
        }

        AccountResult result = accountService.disableAccount(accountId);

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.DISABLE_ACCOUNT,
                null,
                result.getResult(),
                result.getMessage()
        );

        if (result.getResult() == OperationResult.SUCCESS && isCurrentAccount(accountId)) {
            clearCurrentSession();
        }

        return result;
    }

    public AccountResult enableAccount(int accountId) {
        if (!canManageAccounts()) {
            String message = "Only ADMIN can enable accounts.";
            logProtectedActionDenied(ActionType.ENABLE_ACCOUNT, null, message);
            return new AccountResult(OperationResult.FAILED, message);
        }

        AccountResult result = accountService.enableAccount(accountId);

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.ENABLE_ACCOUNT,
                null,
                result.getResult(),
                result.getMessage()
        );

        return result;
    }

    public AccountResult changeOwnPassword(char[] oldPassword, char[] newPassword) {
        if (!isLoggedIn()) {
            String message = "User is not logged in.";
            logProtectedActionDenied(ActionType.CHANGE_PASSWORD, null, message);
            return new AccountResult(OperationResult.FAILED, message);
        }

        PasswordResult passwordResult = accountService.changePassword(
                currentSession.getAccountId(),
                oldPassword,
                newPassword
        );

        if (passwordResult.getResult() == OperationResult.SUCCESS) {
            currentMustChangePassword = false;
        }

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.CHANGE_PASSWORD,
                null,
                passwordResult.getResult(),
                passwordResult.getMessage()
        );

        return new AccountResult(passwordResult.getResult(), passwordResult.getMessage());
    }

    public CryptoResult encryptFile(String inputPath, String outputPath) {
        if (!canUseCrypto()) {
            String message = cryptoAccessFailureMessage();
            logProtectedActionDenied(ActionType.ENCRYPT_FILE, inputPath, message);
            return new CryptoResult(
                    OperationResult.FAILED,
                    message,
                    inputPath,
                    outputPath,
                    ActionType.ENCRYPT_FILE
            );
        }

        return fileCryptoService.encryptFile(inputPath, outputPath, currentUserContext());
    }

    public CryptoResult decryptFile(String inputPath, String outputPath) {
        if (!canUseCrypto()) {
            String message = cryptoAccessFailureMessage();
            logProtectedActionDenied(ActionType.DECRYPT_FILE, inputPath, message);
            return new CryptoResult(
                    OperationResult.FAILED,
                    message,
                    inputPath,
                    outputPath,
                    ActionType.DECRYPT_FILE
            );
        }

        return fileCryptoService.decryptFile(inputPath, outputPath, currentUserContext());
    }

    public List<AccountSummary> searchAccounts(AccountFilter filter) {
        if (!canManageAccounts()) {
            return List.of();
        }

        return accountService.searchAccounts(filter);
    }

    public OperationResult recordAuditLogsAccess() {
        if (!canViewAuditLogs()) {
            logProtectedActionDenied(
                    ActionType.VIEW_LOGS,
                    null,
                    auditLogsAccessFailureMessage()
            );
            return OperationResult.FAILED;
        }

        if (viewLogsLoggedForSelectedRole) {
            return OperationResult.SUCCESS;
        }

        auditService.log(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole(),
                ActionType.VIEW_LOGS,
                null,
                OperationResult.SUCCESS,
                "Audit logs viewed."
        );
        viewLogsLoggedForSelectedRole = true;
        return OperationResult.SUCCESS;
    }

    private void logProtectedActionDenied(ActionType action, String filePath, String message) {
        auditService.log(
                currentSession == null ? null : currentSession.getAccountId(),
                currentSession == null ? null : currentSession.getUsername(),
                currentSession == null ? null : currentSession.getSelectedRole(),
                action,
                filePath,
                OperationResult.FAILED,
                message
        );
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public FileCryptoService getFileCryptoService() {
        return fileCryptoService;
    }

    public boolean hasActiveSession() {
        return isLoggedIn();
    }

    public Role getSelectedRole() {
        return isLoggedIn() ? currentSession.getSelectedRole() : null;
    }

    private boolean isLoggedIn() {
        return currentSession != null;
    }

    private boolean hasSelectedRole(Role role) {
        return isLoggedIn() && currentSession.getSelectedRole() == role;
    }

    private boolean canManageAccounts() {
        return isLoggedIn()
                && !currentMustChangePassword
                && hasSelectedRole(Role.ADMIN);
    }

    private boolean canViewAuditLogs() {
        return isLoggedIn()
                && !currentMustChangePassword
                && hasSelectedRole(Role.AUDITOR);
    }

    private boolean canUseCrypto() {
        return isLoggedIn()
                && !currentMustChangePassword
                && hasSelectedRole(Role.USER)
                // && currentSession.isTokenUnlocked()
                && fileCryptoService.isTokenUnlocked();
    }

    private String cryptoAccessFailureMessage() {
        if (!isLoggedIn()) {
            return "User is not logged in.";
        }

        if (currentMustChangePassword) {
            return "Password must be changed before using the application.";
        }

        if (!hasSelectedRole(Role.USER)) {
            return "Only USER role can encrypt or decrypt files.";
        }

        // if (!currentSession.isTokenUnlocked() || !fileCryptoService.isTokenUnlocked()) {
        if (!fileCryptoService.isTokenUnlocked()) {
            return "Token is not unlocked.";
        }

        return "Crypto operation is not allowed.";
    }

    private String auditLogsAccessFailureMessage() {
        if (!isLoggedIn()) {
            return "User is not logged in.";
        }

        if (currentMustChangePassword) {
            return "Password must be changed before using the application.";
        }

        return "Only AUDITOR role can view audit logs.";
    }

    private UserContext currentUserContext() {
        return new UserContext(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole()
        );
    }

    private boolean isCurrentAccount(int accountId) {
        return isLoggedIn() && currentSession.getAccountId() == accountId;
    }

    private void refreshCurrentSession() {
        if (!isLoggedIn()) {
            return;
        }

        Role previouslySelectedRole = currentSession.getSelectedRole();
        Account account = accountService.getAccountById(currentSession.getAccountId());

        if (account == null || !account.isActive()) {
            clearCurrentSession();
            return;
        }

        Session refreshedSession = new Session(account.getId(), account.getUsername(), account.getRoles());
        if (previouslySelectedRole != null && account.getRoles().contains(previouslySelectedRole)) {
            refreshedSession.selectRole(previouslySelectedRole);
        } else if (fileCryptoService.isTokenUnlocked()) {
            fileCryptoService.lockToken(currentUserContext());
        }

        currentSession = refreshedSession;
        currentMustChangePassword = account.mustChangePassword();
        if (refreshedSession.getSelectedRole() != previouslySelectedRole) {
            resetViewLogsAuditPolicy();
        }
    }

    private void clearCurrentSession() {
        if (fileCryptoService.isTokenUnlocked()) {
            fileCryptoService.lockToken(currentUserContext());
        }
        clearSessionState();
    }

    private void clearSessionState() {
        currentSession = null;
        currentMustChangePassword = false;
        resetViewLogsAuditPolicy();
    }

    private void resetViewLogsAuditPolicy() {
        viewLogsLoggedForSelectedRole = false;
    }

}
