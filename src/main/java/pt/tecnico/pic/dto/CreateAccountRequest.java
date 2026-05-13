package pt.tecnico.pic.dto;

import java.util.Set;

import pt.tecnico.pic.domain.Role;

public class CreateAccountRequest {
    private final String username;
    private final Set<Role> roles;

    public CreateAccountRequest(String username, Set<Role> roles) {
        this.username = username;
        this.roles = Set.copyOf(roles);
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
