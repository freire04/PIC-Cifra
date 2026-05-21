package pt.tecnico.pic.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import pt.tecnico.pic.domain.Account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountStore {
    private final Path accountsFilePath;
    private final ObjectMapper objectMapper;

    public AccountStore(String accountsFilePath) {
        this(Path.of(accountsFilePath));
    }
    
    public AccountStore(Path accountsFilePath) {
        this.accountsFilePath = accountsFilePath;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void save(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        List<Account> accounts = readAccounts();

        for (int i = 0; i < accounts.size(); i++) {
            Account current = accounts.get(i);

            if (current.getId() == account.getId()) {
                accounts.set(i, account);
                writeAccounts(accounts);
                return;
            }
        }

        accounts.add(account);
        writeAccounts(accounts);
    }

    public Optional<Account> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return readAccounts().stream()
                .filter(account -> username.equals(account.getUsername()))
                .findFirst();
    }

    public Optional<Account> findById(int accountId) {
        return readAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst();
    }


    public List<Account> findAll() {
        return new ArrayList<>(readAccounts());
    }

    public List<Account> findActive() {
        return readAccounts().stream()
                .filter(Account::isActive)
                .toList();
    }

    public List<Account> findDisabled() {
        return readAccounts().stream()
                .filter(account -> !account.isActive())
                .toList();
    }

    
    public int getNextId() {
        return readAccounts().stream()
                .mapToInt(Account::getId)
                .max()
                .orElse(0) + 1;
    }

    private List<Account> readAccounts() {
        try {
            if (!Files.exists(accountsFilePath)) {
                return new ArrayList<>();
            }

            if (Files.size(accountsFilePath) == 0) {
                return new ArrayList<>();
            }

            return objectMapper.readValue(
                    accountsFilePath.toFile(),
                    new TypeReference<List<Account>>() {}
            );
        } catch (IOException e) {
            throw new AccountStoreException("Failed to read accounts file", e);
        }
    }

    private void writeAccounts(List<Account> accounts) {
        try {
            Path parent = accountsFilePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            objectMapper.writeValue(accountsFilePath.toFile(), accounts);
        } catch (IOException e) {
            throw new AccountStoreException("Failed to write accounts file", e);
        }
    }
}