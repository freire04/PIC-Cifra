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
            currentSession = null;
            currentMustChangePassword = false;

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

        // If the user is not logged in, we can consider the logout successful
        // isto é mais para casos de debugs do que para casos reais
        if (!isLoggedIn()) {
            return OperationResult.FAILED;
        }

        Integer accountId = currentSession.getAccountId();
        String username = currentSession.getUsername();
        Role actorRole = currentSession.getSelectedRole();

        OperationResult result = OperationResult.SUCCESS;

        if (currentSession.isTokenUnlocked() || fileCryptoService.isTokenUnlocked()) {
            result = fileCryptoService.lockToken();
            currentSession.lockToken();
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
            return new RoleSelectionResult(
                    OperationResult.FAILED,
                    "User is not logged in.",
                    null,
                    false
            );
        }

        if (currentMustChangePassword) {
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
                    currentSession.isTokenUnlocked()
            );
        }

        if (role == Role.USER) {
            OperationResult unlockResult = fileCryptoService.unlockToken(tokenPin);

            if (unlockResult != OperationResult.SUCCESS) {
                currentSession.lockToken();

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
            currentSession.unlockToken();

        } else {
            if (currentSession.isTokenUnlocked() || fileCryptoService.isTokenUnlocked()) {
                fileCryptoService.lockToken();
            }

            currentSession.selectRole(role);
            currentSession.lockToken();
        }

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
                currentSession.isTokenUnlocked()
        );
    }

    public CryptoResult encryptFile(String inputPath, String outputPath) {
        if (!canUseCrypto()) {
            return new CryptoResult(
                    OperationResult.FAILED,
                    cryptoAccessFailureMessage(),
                    inputPath,
                    outputPath,
                    ActionType.ENCRYPT_FILE
            );
        }

        return fileCryptoService.encryptFile(inputPath, outputPath, currentUserContext());
    }

    public CryptoResult decryptFile(String inputPath, String outputPath) {
        if (!canUseCrypto()) {
            return new CryptoResult(
                    OperationResult.FAILED,
                    cryptoAccessFailureMessage(),
                    inputPath,
                    outputPath,
                    ActionType.DECRYPT_FILE
            );
        }

        return fileCryptoService.decryptFile(inputPath, outputPath, currentUserContext());
    }

    public List<AccountSummary> getUsers() {
        if (!canManageAccounts()) {
            return List.of();
        }

        return accountService.listAccounts()
                .stream()
                .map(this::toAccountSummary)
                .toList();
    }

    public AccountCreationResult createAccount(CreateAccountRequest request) {
        if (!canManageAccounts()) {
            return new AccountCreationResult(
                    OperationResult.FAILED,
                    -1,
                    null,
                    null,
                    "Only ADMIN can create accounts."
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
            return new AccountResult(OperationResult.FAILED, "Only ADMIN can update user roles.");
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

        return result;
    }

    public PasswordResult resetPassword(int accountId) {
        if (!canManageAccounts()) {
            return new PasswordResult(OperationResult.FAILED, "Only ADMIN can reset passwords.", null);
        }

        PasswordResult result = accountService.resetPassword(accountId);

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
            return new AccountResult(OperationResult.FAILED, "Only ADMIN can disable accounts.");
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

        return result;
    }

    public AccountResult enableAccount(int accountId) {
        if (!canManageAccounts()) {
            return new AccountResult(OperationResult.FAILED, "Only ADMIN can enable accounts.");
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
            return new AccountResult(OperationResult.FAILED, "User is not logged in.");
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

    public void recordLogin(Integer accountId, String username, OperationResult result, String message) {
        auditService.log(accountId, username, null, ActionType.LOGIN, null, result, message);
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public FileCryptoService getFileCryptoService() {
        return fileCryptoService;
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

    private boolean canUseCrypto() {
        return isLoggedIn()
                && !currentMustChangePassword
                && hasSelectedRole(Role.USER)
                && currentSession.isTokenUnlocked()
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

        if (!currentSession.isTokenUnlocked() || !fileCryptoService.isTokenUnlocked()) {
            return "Token is not unlocked.";
        }

        return "Crypto operation is not allowed.";
    }

    private UserContext currentUserContext() {
        return new UserContext(
                currentSession.getAccountId(),
                currentSession.getUsername(),
                currentSession.getSelectedRole()
        );
    }

    private AccountSummary toAccountSummary(Account account) {
        return new AccountSummary(
                account.getId(),
                account.getUsername(),
                account.getRoles(),
                account.isActive(),
                account.mustChangePassword()
        );
    }
}