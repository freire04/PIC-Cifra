package pt.tecnico.pic.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import pt.tecnico.pic.domain.Account;

/**
 * Minimal in-memory AccountStore used by the current codebase until the JSON
 * persistence issue is merged. It keeps disabled accounts too, so usernames are
 * globally unique and cannot be reused after deactivation.
 */
public class AccountStore {
    private final Map<Integer, Account> accountsById = new HashMap<>();
    private final Map<String, Integer> accountIdsByUsername = new HashMap<>();

    public AccountStore() {
    }

    public synchronized void save(Account account) {
        Objects.requireNonNull(account, "account must not be null");

        Account existingAccount = accountsById.get(account.getId());
        if (existingAccount != null && !existingAccount.getUsername().equals(account.getUsername())) {
            throw new IllegalArgumentException("Username is immutable.");
        }

        Integer existingIdForUsername = accountIdsByUsername.get(account.getUsername());
        if (existingIdForUsername != null && existingIdForUsername != account.getId()) {
            throw new IllegalArgumentException("Username already exists.");
        }

        accountsById.put(account.getId(), account);
        accountIdsByUsername.put(account.getUsername(), account.getId());
    }

    public synchronized Account findByUsername(String username) {
        if (username == null) {
            return null;
        }

        Integer accountId = accountIdsByUsername.get(username.trim().toLowerCase());
        if (accountId == null) {
            return null;
        }

        return accountsById.get(accountId);
    }

    public synchronized Account findById(int accountId) {
        return accountsById.get(accountId);
    }

    public synchronized List<Account> findAll() {
        return accountsById.values()
                .stream()
                .sorted(Comparator.comparingInt(Account::getId))
                .toList();
    }

    public synchronized List<Account> findActive() {
        return accountsById.values()
                .stream()
                .filter(Account::isActive)
                .sorted(Comparator.comparingInt(Account::getId))
                .toList();
    }

    public synchronized List<Account> findDisabled() {
        return accountsById.values()
                .stream()
                .filter(account -> !account.isActive())
                .sorted(Comparator.comparingInt(Account::getId))
                .toList();
    }
}
