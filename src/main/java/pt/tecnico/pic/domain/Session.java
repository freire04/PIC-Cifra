package pt.tecnico.pic.domain;

import java.util.Set;

public class Session {

    private final int accountId;
    private final String username;
    private final Set<Role> availableRoles;
    private Role selectedRole;

    public Session(int accountId, String username, Set<Role> availableRoles) {
        this.accountId = accountId;
        this.username = username;
        this.availableRoles = Set.copyOf(availableRoles);
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

    public Role getSelectedRole() {
        return selectedRole;
    }

    public void selectRole(Role role) {
        if (!availableRoles.contains(role)) {
            throw new IllegalArgumentException("Role not available for this session.");
        }

        this.selectedRole = role;
    }
    
}