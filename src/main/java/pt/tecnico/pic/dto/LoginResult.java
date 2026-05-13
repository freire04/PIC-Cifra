package pt.tecnico.pic.dto;


import java.util.Set;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public final class LoginResult {
    private final OperationResult result;
    private final String message;
    private final int accountId;
    private final String username;
    private final Set<Role> availableRoles;
    private final boolean mustChangePassword;


    public LoginResult(OperationResult result, String message, int accountId, String username, Set<Role> availableRoles, boolean mustChangePassword) {
        this.result = result;
        this.message = message;
        this.accountId = accountId;
        this.username = username;
        this.availableRoles = Set.copyOf(availableRoles);
        this.mustChangePassword = mustChangePassword;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getAvailableRoles() {
        return availableRoles;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }
}
