package pt.tecnico.pic.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import pt.tecnico.pic.domain.Account;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.store.AccountStore;

public class AccountService {
    private final AccountStore accountStore;
    private final PasswordService passwordService;

    public AccountService() {
        this(new AccountStore(), new PasswordService());
    }

    public AccountService(AccountStore accountStore, PasswordService passwordService) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.passwordService = Objects.requireNonNull(passwordService, "passwordService must not be null");
    }

    public Account authenticate(String username, char[] password) {
        try {
            String normalizedUsername = normalizeUsername(username);

            Account account = accountStore.findByUsername(normalizedUsername).orElse(null);
            if (account == null || !account.isActive()) {
                return null;
            }

            if (!passwordService.verifyPassword(password, account.getPasswordHash())) {
                return null;
            }

            return account;

        } catch (IllegalArgumentException e) {
            return null;

        } finally {
            passwordService.clear(password);
        }
    }

    public synchronized AccountCreationResult createAccount(String username, Set<Role> roles) {
        char[] temporaryPassword = null;

        try {
            String normalizedUsername = normalizeUsername(username);
            Set<Role> safeRoles = validateRoles(roles);

            if (accountStore.findByUsername(normalizedUsername).isPresent()) {
                return new AccountCreationResult(
                        OperationResult.FAILED,
                        -1,
                        normalizedUsername,
                        null,
                        "Username already exists."
                );
            }

            temporaryPassword = passwordService.generateTemporaryPassword();
            String passwordHash = passwordService.hashPassword(temporaryPassword);

            int accountId = accountStore.getNextId();

            Account account = new Account(accountId, normalizedUsername, passwordHash, safeRoles, true);
            accountStore.save(account);

            return new AccountCreationResult(
                    OperationResult.SUCCESS,
                    accountId,
                    normalizedUsername,
                    temporaryPassword,
                    "Account created successfully."
            );

        } catch (IllegalArgumentException e) {
            return new AccountCreationResult(OperationResult.FAILED, -1, username, null, e.getMessage());

        } catch (RuntimeException e) {
            return new AccountCreationResult(OperationResult.ERROR, -1, username, null, "Could not create account.");

        } finally {
            passwordService.clear(temporaryPassword);
        }
    }

    public Account getAccountById(int accountId) {
        return accountStore.findById(accountId).orElse(null);
    }

    public List<Account> listAccounts() {
        return accountStore.findAll();
    }

    public synchronized AccountResult updateRoles(int accountId, Set<Role> roles) {
        try {
            Account account = accountStore.findById(accountId).orElse(null);
            if (account == null) {
                return new AccountResult(OperationResult.FAILED, "Account not found.");
            }

            account.setRoles(validateRoles(roles));
            accountStore.save(account);

            return new AccountResult(OperationResult.SUCCESS, "Roles updated successfully.");

        } catch (IllegalArgumentException e) {
            return new AccountResult(OperationResult.FAILED, e.getMessage());

        } catch (RuntimeException e) {
            return new AccountResult(OperationResult.ERROR, "Could not update roles.");
        }
    }

    public synchronized PasswordResult changePassword(int accountId, char[] oldPassword, char[] newPassword) {
        try {
            Account account = accountStore.findById(accountId).orElse(null);
            if (account == null) {
                return new PasswordResult(OperationResult.FAILED, "Account not found.", null);
            }

            if (!account.isActive()) {
                return new PasswordResult(OperationResult.FAILED, "Account is disabled.", null);
            }

            if (!passwordService.verifyPassword(oldPassword, account.getPasswordHash())) {
                return new PasswordResult(OperationResult.FAILED, "Old password is incorrect.", null);
            }

            requirePassword(newPassword);

            String newPasswordHash = passwordService.hashPassword(newPassword);
            account.changePassword(newPasswordHash);
            accountStore.save(account);

            return new PasswordResult(OperationResult.SUCCESS, "Password changed successfully.", null);

        } catch (IllegalArgumentException e) {
            return new PasswordResult(OperationResult.FAILED, e.getMessage(), null);

        } catch (RuntimeException e) {
            return new PasswordResult(OperationResult.ERROR, "Could not change password.", null);

        } finally {
            passwordService.clear(oldPassword);
            passwordService.clear(newPassword);
        }
    }

    public synchronized PasswordResult resetPassword(int accountId) {
        char[] temporaryPassword = null;

        try {
            Account account = accountStore.findById(accountId).orElse(null);
            if (account == null) {
                return new PasswordResult(OperationResult.FAILED, "Account not found.", null);
            }

            temporaryPassword = passwordService.generateTemporaryPassword();
            String passwordHash = passwordService.hashPassword(temporaryPassword);

            account.resetPassword(passwordHash);
            accountStore.save(account);

            return new PasswordResult(
                    OperationResult.SUCCESS,
                    "Password reset successfully.",
                    temporaryPassword
            );

        } catch (RuntimeException e) {
            return new PasswordResult(OperationResult.ERROR, "Could not reset password.", null);

        } finally {
            passwordService.clear(temporaryPassword);
        }
    }

    public synchronized AccountResult disableAccount(int accountId) {
        try {
            Account account = accountStore.findById(accountId).orElse(null);
            if (account == null) {
                return new AccountResult(OperationResult.FAILED, "Account not found.");
            }

            account.deactivate();
            accountStore.save(account);

            return new AccountResult(OperationResult.SUCCESS, "Account disabled successfully.");

        } catch (RuntimeException e) {
            return new AccountResult(OperationResult.ERROR, "Could not disable account.");
        }
    }

    public synchronized AccountResult enableAccount(int accountId) {
        try {
            Account account = accountStore.findById(accountId).orElse(null);
            if (account == null) {
                return new AccountResult(OperationResult.FAILED, "Account not found.");
            }

            account.activate();
            accountStore.save(account);

            return new AccountResult(OperationResult.SUCCESS, "Account enabled successfully.");

        } catch (RuntimeException e) {
            return new AccountResult(OperationResult.ERROR, "Could not enable account.");
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty.");
        }

        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Set<Role> validateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required.");
        }

        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Roles cannot contain null values.");
        }

        return Set.copyOf(roles);
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
    }
}