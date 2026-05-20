package pt.tecnico.pic.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import pt.tecnico.pic.domain.Account;
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
        return null;
    }

    public AccountCreationResult createAccount(String username, Set<Role> roles) {
        return null;
    }

    public Account getAccountById(int accountId) {
        return null;
    }

    public List<Account> listAccounts(){
        return null;
    }

    public AccountResult updateRoles(int accountId, Set<Role> roles) {
        return null;
    }

    public PasswordResult changePassword(int accountId, char[] oldPassword, char[] newPassword) {
        return null;
    }

    public PasswordResult resetPassword(int accountId) {
        return null; 
    }

    public AccountResult deleteAccount(int accountId) {
        return null;
    }

    public AccountResult disableAccount(int accountId) {
        return null;
    }

    public AccountResult enableAccount(int accountId) {
        return null;
    }

}
