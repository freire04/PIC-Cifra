package pt.tecnico.pic.domain;

public class UserContext {
    private final int accountId;
    private final String username;
    private final Role selectedRole;

    public UserContext(int accountId, String username, Role selectedRole) {
        this.accountId = accountId;
        this.username = username;
        this.selectedRole = selectedRole;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public Role getSelectedRole() {
        return selectedRole;
    }
    
}
