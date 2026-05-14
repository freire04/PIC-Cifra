package pt.tecnico.pic.store;

import java.util.ArrayList;
import java.util.List;

import pt.tecnico.pic.domain.Account;

public class AccountStore {

    public AccountStore() {
    }

    public void save(Account account) {
    }

    public Account findByUsername(String username) {
        return null;
    }

    public Account findById(int accountId) {
        return null;
    }

    public List<Account> findAll() {
        return new ArrayList<>();
    }

    public List<Account> findActive() {
        return new ArrayList<>();
    }

    public List<Account> findDisabled() {
        return new ArrayList<>();
    }
}