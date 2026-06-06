package pt.tecnico.pic.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        this(id, username, passwordHash, roles, active, true);
    }

    @JsonCreator
    public Account(
            @JsonProperty("id") int id,
            @JsonProperty("username") String username,
            @JsonProperty("passwordHash") String passwordHash,
            @JsonProperty("roles") Set<Role> roles,
            @JsonProperty("active") boolean active,
            @JsonProperty("mustChangePassword") boolean mustChangePassword
    ) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.roles = copyValidatedRoles(roles);
        this.active = active;
        this.mustChangePassword = mustChangePassword;
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

    @JsonProperty("mustChangePassword")
    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = copyValidatedRoles(roles);
    }

    public void addRole(Role role) {
        this.roles.add(Objects.requireNonNull(role, "role must not be null"));
    }

    public void removeRole(Role role) {
        this.roles.remove(Objects.requireNonNull(role, "role must not be null"));
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        this.mustChangePassword = false;
    }

    public void resetPassword(String temporaryPasswordHash) {
        this.passwordHash = Objects.requireNonNull(temporaryPasswordHash, "temporaryPasswordHash must not be null");
        this.mustChangePassword = true;
    }

    private static Set<Role> copyValidatedRoles(Set<Role> roles) {
        Objects.requireNonNull(roles, "roles must not be null");

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required.");
        }

        for (Role role : roles) {
            if (role == null) {
                throw new NullPointerException("Role set cannot contain null elements.");
            }
        }

        return new HashSet<>(roles);
    }
}