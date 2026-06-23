package pt.tecnico.pic.presentation.controller;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.presentation.controller.DashboardViewController.DashboardAction;

class DashboardViewControllerTest {

    @Test
    void auditorShouldSeeViewLogsAction() {
        List<DashboardAction> actions = DashboardViewController.actionsFor(Role.AUDITOR);

        assertEquals(
                List.of(DashboardAction.VIEW_LOGS, DashboardAction.CHANGE_PASSWORD),
                actions
        );
    }

    @Test
    void userShouldNotSeeViewLogsAction() {
        List<DashboardAction> actions = DashboardViewController.actionsFor(Role.USER);

        assertFalse(actions.contains(DashboardAction.VIEW_LOGS));
        assertTrue(actions.contains(DashboardAction.ENCRYPT_FILE));
        assertTrue(actions.contains(DashboardAction.DECRYPT_FILE));
    }

    @Test
    void adminShouldNotSeeViewLogsActionWhenAdminRoleIsActive() {
        List<DashboardAction> actions = DashboardViewController.actionsFor(Role.ADMIN);

        assertFalse(actions.contains(DashboardAction.VIEW_LOGS));
        assertTrue(actions.contains(DashboardAction.MANAGE_USERS));
    }

    @Test
    void noSelectedRoleShouldExposeNoActions() {
        assertTrue(DashboardViewController.actionsFor(null).isEmpty());
    }
}
