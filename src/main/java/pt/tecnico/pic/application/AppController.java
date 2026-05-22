package pt.tecnico.pic.application;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.ChangePasswordResult;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;

/**
 * Central application facade.
 */

// TODO (S1-10):
// AppController should later receive AccountService and expose the
// full application flows (login, role selection, account management, etc.).

public class AppController {

    private final AccountService accountService;
    private final AuditService auditService;
    private final FileCryptoService fileCryptoService;

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
        // TODO (S1-10): delegate to AccountService.authenticate(...) when authentication is implemented.
        // Placeholder for successful login
        if ("abc".equals(username) && Arrays.equals(password, "123".toCharArray())) {
            return new LoginResult(
                    OperationResult.SUCCESS,
                    "Login successful.",
                    -1,
                    username,
                    Set.of(),
                    false
            ); 
        }

        // Placeholder for password change flow
        else if ("teste".equals(username) && Arrays.equals(password, "123".toCharArray())) {
            return new LoginResult(
                    OperationResult.SUCCESS,
                    "Login successful.",
                    -1,
                    username,
                    Set.of(),
                    true
            ); 
        }
        
        // Placeholder for failed login
        return new LoginResult(
                OperationResult.ERROR,
                "Login failed.",
                -1,
                username,
                Set.of(),
                false
        );
    }

    public ChangePasswordResult changeOwnPassword(char[] oldPassword, char[] newPassword) {
        // TODO: delegate to AccountService.changePassword(...) or similar when implemented.

        // Placeholder for successful password change
        if (Arrays.equals(oldPassword, "old".toCharArray()) && Arrays.equals(newPassword, "new".toCharArray())) {
            return new ChangePasswordResult(OperationResult.SUCCESS, "Password changed successfully.");
        }

        // Placeholder for new password equal to old password
        if (Arrays.equals(oldPassword, newPassword) && oldPassword.length > 0) {
            return new ChangePasswordResult(OperationResult.FAILED, "New password cannot be the same as the old password.");
        }

        // Placeholder for failed password change
        return new ChangePasswordResult(OperationResult.ERROR, "Password change failed.");
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
}
