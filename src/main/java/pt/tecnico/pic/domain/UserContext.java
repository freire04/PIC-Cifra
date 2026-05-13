package pt.tecnico.pic.domain;

public class UserContext {
    private final int accountId;
    private final String username;
    private final Role role;

    public UserContext(int accountId, String username, Role role) {
        this.accountId = accountId;
        this.username = username;
        this.role = role;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }
    
}
