package pt.tecnico.pic.dto;

import java.util.Set;

import pt.tecnico.pic.domain.Role;

public class AccountSummary {
    private final int accountId;
    private final String username;
    private final Set<Role> roles;
    private final boolean active;
    private final boolean mustChangePassword;

    public AccountSummary(int accountId, String username, Set<Role> roles, boolean active, boolean mustChangePassword) {
        this.accountId = accountId;
        this.username = username;
        this.roles = Set.copyOf(roles);
        this.active = active;
        this.mustChangePassword = mustChangePassword;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isActive() {
        return active;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }
}
