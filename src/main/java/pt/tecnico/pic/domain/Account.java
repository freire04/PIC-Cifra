package pt.tecnico.pic.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Account {
    private final int id;
    private final String username;
    private String passwordHash;
    private Set<Role> roles;
    private boolean active;
    private boolean mustChangePassword;

    public Account(int id, String username, String passwordHash, Set<Role> roles, boolean active) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = copyValidatedRoles(roles);
        this.active = active;
        this.mustChangePassword = true;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean isActive() {
        return active;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = copyValidatedRoles(roles);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    /**
     * Creates a defensive mutable copy of roles after validating that the set
     * and all of its elements are non-null.
     */
    private static Set<Role> copyValidatedRoles(Set<Role> roles) {
        Objects.requireNonNull(roles);
        for (Role role : roles) {
            if (role == null) {
                throw new NullPointerException("Role set cannot contain null elements");
            }
        }
        return new HashSet<>(roles);
    }
}
