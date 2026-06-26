package pt.tecnico.pic.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogFilter;

class AuditLogViewControllerTest {

    @Test
    void buildFilterShouldTrimUsernameAndIncludeSelectedFilters() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 23);

        LogFilter filter = AuditLogViewController.buildFilter(
                " alice ",
                Role.AUDITOR,
                ActionType.ENCRYPT_FILE,
                OperationResult.SUCCESS,
                startDate,
                endDate
        );

        assertEquals("alice", filter.getUsername());
        assertEquals(Role.AUDITOR, filter.getActorRole());
        assertEquals(ActionType.ENCRYPT_FILE, filter.getActionType());
        assertEquals(OperationResult.SUCCESS, filter.getResult());
        assertEquals(startDate.atStartOfDay(), filter.getStartDate());
        assertEquals(endDate.atTime(LocalTime.MAX), filter.getEndDate());
    }

    @Test
    void buildFilterShouldIgnoreBlankUsernameAndMissingFilters() {
        LogFilter filter = AuditLogViewController.buildFilter("   ", null, null, null, null, null);

        assertNull(filter.getUsername());
        assertNull(filter.getActorRole());
        assertNull(filter.getActionType());
        assertNull(filter.getResult());
        assertNull(filter.getFileName());
        assertNull(filter.getStartDate());
        assertNull(filter.getEndDate());
    }

    @Test
    void buildFilterShouldRejectStartDateAfterEndDate() {
        LocalDate startDate = LocalDate.of(2026, 6, 23);
        LocalDate endDate = LocalDate.of(2026, 6, 1);

        assertThrows(IllegalArgumentException.class, () ->
                AuditLogViewController.buildFilter(null, null, null, null, startDate, endDate)
        );
    }

    @Test
    void formatTimestampShouldUseReadableTableFormat() {
        assertEquals(
                "2026-06-23 14:05:06",
                AuditLogViewController.formatTimestamp(LocalDate.of(2026, 6, 23).atTime(14, 5, 6))
        );
    }

    @Test
    void resultStyleShouldColorOperationResults() {
        assertTrue(AuditLogViewController.resultStyle("SUCCESS").contains("#166534"));
        assertTrue(AuditLogViewController.resultStyle("FAILED").contains("#991b1b"));
        assertTrue(AuditLogViewController.resultStyle("ERROR").contains("#a16207"));
    }
}
