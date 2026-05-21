package pt.tecnico.pic.application;

import java.util.Objects;
import java.util.Set;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
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

    private static final String PLACEHOLDER_PASSWORD = "123";
    private static final char[] EMPTY_PASSWORD = new char[0];

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
        if ("abc".equals(username) && isPlaceholderPassword(password)) {
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
        else if ("teste".equals(username) && isPlaceholderPassword(password)) {
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

    private boolean isPlaceholderPassword(char[] password) {
        char[] candidate = password == null ? EMPTY_PASSWORD : password;
        int maxLength = Math.max(candidate.length, PLACEHOLDER_PASSWORD.length());
        int differenceAccumulator = candidate.length ^ PLACEHOLDER_PASSWORD.length();

        for (int i = 0; i < maxLength; i++) {
            char candidateValue = i < candidate.length ? candidate[i] : 0;
            char placeholderValue = i < PLACEHOLDER_PASSWORD.length() ? PLACEHOLDER_PASSWORD.charAt(i) : 0;
            differenceAccumulator |= candidateValue ^ placeholderValue;
        }

        return differenceAccumulator == 0;
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
