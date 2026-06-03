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
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
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
        this.roles.add(Objects.requireNonNull(role));
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
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
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
