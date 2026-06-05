package pt.tecnico.pic.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.io.TempDir;

import java.util.Set;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.Account;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.store.AccountStore;


class AccountServiceTest {

    @TempDir
    Path tempDir;

    private AccountService newAccountService() {
        AccountStore accountStore = new AccountStore(tempDir.resolve("accounts.json"));
        PasswordService passwordService = new PasswordService();
        return new AccountService(accountStore, passwordService);
    }

    private AccountStore newAccountStore() {
        return new AccountStore(tempDir.resolve("accounts.json"));
    }

    @Test
    void createAccountShouldGenerateTemporaryPasswordAndHashOnly() {
        AccountStore accountStore = newAccountStore();
        PasswordService passwordService = new PasswordService();
        AccountService accountService = new AccountService(accountStore, passwordService);

        AccountCreationResult result = accountService.createAccount("alice", Set.of(Role.USER));

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals("alice", result.getUsername());
        assertNotNull(result.getTemporaryPassword());

        Account account = accountStore.findByUsername("alice").orElseThrow();

        assertNotNull(account);
        assertTrue(account.isActive());
        assertTrue(account.mustChangePassword());
        assertFalse(account.getPasswordHash().contains(result.getTemporaryPassword()));
    }

    @Test
    void authenticateShouldSucceedWithTemporaryPassword() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("bob", Set.of(Role.USER));
        Account authenticated = accountService.authenticate("bob", created.getTemporaryPassword().toCharArray());

        assertNotNull(authenticated);
        assertEquals("bob", authenticated.getUsername());
    }

    @Test
    void authenticateShouldFailWithWrongPassword() {
        AccountService accountService = newAccountService();

        accountService.createAccount("bob", Set.of(Role.USER));

        Account authenticated = accountService.authenticate("bob", "wrong-password".toCharArray());

        assertNull(authenticated);
    }

    @Test
    void authenticateShouldFailForDisabledAccount() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("bob", Set.of(Role.USER));
        accountService.disableAccount(created.getAccountId());

        Account authenticated = accountService.authenticate("bob", created.getTemporaryPassword().toCharArray());

        assertNull(authenticated);
    }

    @Test
    void createAccountShouldRejectRepeatedUsernameEvenAfterDisable() {
        AccountService accountService = newAccountService();

        AccountCreationResult first = accountService.createAccount("bob", Set.of(Role.USER));
        accountService.disableAccount(first.getAccountId());

        AccountCreationResult second = accountService.createAccount("bob", Set.of(Role.ADMIN));

        assertEquals(OperationResult.FAILED, second.getResult());
    }

    @Test
    void updateRolesShouldReplaceRoles() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("carol", Set.of(Role.USER));
        AccountResult result = accountService.updateRoles(created.getAccountId(), Set.of(Role.ADMIN));

        Account account = accountService.getAccountById(created.getAccountId());

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals(Set.of(Role.ADMIN), account.getRoles());
    }

    @Test
    void changePasswordShouldRequireOldPasswordAndClearMustChangePassword() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("dave", Set.of(Role.USER));

        PasswordResult wrong = accountService.changePassword(
                created.getAccountId(),
                "wrong".toCharArray(),
                "NewPassword123!".toCharArray()
        );

        assertEquals(OperationResult.FAILED, wrong.getResult());

        PasswordResult correct = accountService.changePassword(
                created.getAccountId(),
                created.getTemporaryPassword().toCharArray(),
                "NewPassword123!".toCharArray()
        );

        Account account = accountService.getAccountById(created.getAccountId());

        assertEquals(OperationResult.SUCCESS, correct.getResult());
        assertFalse(account.mustChangePassword());
        assertNotNull(accountService.authenticate("dave", "NewPassword123!".toCharArray()));
    }

    @Test
    void resetPasswordShouldGenerateNewTemporaryPasswordAndForcePasswordChange() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("erin", Set.of(Role.USER));

        accountService.changePassword(
                created.getAccountId(),
                created.getTemporaryPassword().toCharArray(),
                "NormalPassword123!".toCharArray()
        );

        PasswordResult reset = accountService.resetPassword(created.getAccountId());
        Account account = accountService.getAccountById(created.getAccountId());

        assertEquals(OperationResult.SUCCESS, reset.getResult());
        assertNotNull(reset.getTemporaryPassword());
        assertTrue(account.mustChangePassword());
        assertNotNull(accountService.authenticate("erin", reset.getTemporaryPassword().toCharArray()));
    }

    @Test
    void disableAndEnableAccountShouldChangeActiveFlag() {
        AccountService accountService = newAccountService();

        AccountCreationResult created = accountService.createAccount("frank", Set.of(Role.ADMIN));

        AccountResult disabled = accountService.disableAccount(created.getAccountId());
        assertEquals(OperationResult.SUCCESS, disabled.getResult());
        assertFalse(accountService.getAccountById(created.getAccountId()).isActive());

        AccountResult enabled = accountService.enableAccount(created.getAccountId());
        assertEquals(OperationResult.SUCCESS, enabled.getResult());
        assertTrue(accountService.getAccountById(created.getAccountId()).isActive());
    }
}