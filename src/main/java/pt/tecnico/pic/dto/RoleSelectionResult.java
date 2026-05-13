package pt.tecnico.pic.dto;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

public class RoleSelectionResult {
    private final OperationResult result;
    private final String message;
    private final Role selectedRole;
    private final boolean tokenUnlocked;

    public RoleSelectionResult(OperationResult result, String message, Role selectedRole, boolean tokenUnlocked) {
        this.result = result;
        this.message = message;
        this.selectedRole = selectedRole;
        this.tokenUnlocked = tokenUnlocked;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public Role getSelectedRole() {
        return selectedRole;
    }

    public boolean isTokenUnlocked() {
        return tokenUnlocked;
    }
}
