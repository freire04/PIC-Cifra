package pt.tecnico.pic.presentation.controller;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.Role;

class RoleSelectionViewControllerTest {

    @Test
    void availableAuditorRoleShouldBeDisplayed() {
        List<Role> roles = RoleSelectionViewController.rolesInDisplayOrder(
                Set.of(Role.ADMIN, Role.AUDITOR)
        );

        assertEquals(List.of(Role.AUDITOR, Role.ADMIN), roles);
    }

    @Test
    void unavailableRolesShouldNotBeDisplayed() {
        List<Role> roles = RoleSelectionViewController.rolesInDisplayOrder(Set.of(Role.USER));

        assertEquals(List.of(Role.USER), roles);
    }

    @Test
    void onlyUserRoleShouldRequireTokenPin() {
        assertTrue(RoleSelectionViewController.requiresTokenPin(Role.USER));
        assertFalse(RoleSelectionViewController.requiresTokenPin(Role.AUDITOR));
        assertFalse(RoleSelectionViewController.requiresTokenPin(Role.ADMIN));
    }
}
