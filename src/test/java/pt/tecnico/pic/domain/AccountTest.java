package pt.tecnico.pic.domain;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void constructorShouldInitializeAccount() {
        Set<Role> roles = new HashSet<>(Set.of(Role.USER));

        Account account = new Account(1, "alice", "hash123", roles, true);

        assertEquals(1, account.getId());
        assertEquals("alice", account.getUsername());
        assertEquals("hash123", account.getPasswordHash());
        assertEquals(roles, account.getRoles());
        assertTrue(account.isActive());
        assertTrue(account.mustChangePassword());
    }

    @Test
    void deactivateAndActivateShouldChangeActiveState() {
        Account account = new Account(1, "alice", "hash123", new HashSet<>(Set.of(Role.USER)), true);

        account.deactivate();
        assertFalse(account.isActive());

        account.activate();
        assertTrue(account.isActive());
    }

    @Test
    void changePasswordShouldUpdateHashAndClearMustChangePassword() {
        Account account = new Account(1, "alice", "oldHash", new HashSet<>(Set.of(Role.USER)), true);

        account.changePassword("newHash");

        assertEquals("newHash", account.getPasswordHash());
        assertFalse(account.mustChangePassword());
    }

    @Test
    void addRoleShouldAddRoleToAccount() {
        Account account = new Account(1, "alice", "hash123", new HashSet<>(Set.of(Role.USER)), true);

        account.addRole(Role.ADMIN);

        assertTrue(account.getRoles().contains(Role.USER));
        assertTrue(account.getRoles().contains(Role.ADMIN));
    }
}