package pt.tecnico.pic.dto;

import java.util.Set;

import pt.tecnico.pic.domain.Role;

public class AccountFilter {
    private String username;
    private Set<Role> roles;
    private AccountStatusFilter status;

    public AccountFilter() {}

    public AccountFilter(String username, Set<Role> roles, AccountStatusFilter status) {
        this.username = username;
        this.roles = roles;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public AccountStatusFilter getStatus() {
        return status;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void setStatus(AccountStatusFilter status) {
        this.status = status;
    }
}
