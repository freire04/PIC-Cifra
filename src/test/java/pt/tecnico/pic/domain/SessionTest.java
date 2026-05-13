package pt.tecnico.pic.domain;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SessionTest {

    @Test
    void constructorShouldInitializeSession() {
        Set<Role> roles = Set.of(Role.USER, Role.ADMIN);

        Session session = new Session(1, "alice", roles);

        assertEquals(1, session.getAccountId());
        assertEquals("alice", session.getUsername());
        assertEquals(roles, session.getAvailableRoles());
        assertNull(session.getSelectedRole());
        assertFalse(session.isTokenUnlocked());
    }

    @Test
    void selectRoleShouldUpdateSelectedRole() {
        Session session = new Session(1, "alice", Set.of(Role.USER, Role.ADMIN));

        session.selectRole(Role.ADMIN);

        assertEquals(Role.ADMIN, session.getSelectedRole());
    }

    @Test
    void selectRoleShouldRejectUnavailableRole() {
        Session session = new Session(1, "alice", Set.of(Role.USER));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> session.selectRole(Role.ADMIN)
        );

        assertEquals("Role not available for this session.", ex.getMessage());
    }

    @Test
    void unlockAndLockTokenShouldChangeTokenState() {
        Session session = new Session(1, "alice", Set.of(Role.USER));

        session.unlockToken();
        assertTrue(session.isTokenUnlocked());

        session.lockToken();
        assertFalse(session.isTokenUnlocked());
    }
}