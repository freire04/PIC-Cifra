package pt.tecnico.pic.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.tecnico.pic.domain.Account;
import pt.tecnico.pic.domain.Role;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccountStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveCreatesAccountsFileAndFindsAccount() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        Account account = new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        );

        store.save(account);

        assertTrue(Files.exists(accountsPath));
        assertTrue(store.findByUsername("afonso").isPresent());
        assertTrue(store.findById(1).isPresent());
        assertEquals(1, store.findAll().size());
    }

    @Test
    void saveUpdatesExistingAccountWithSameId() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        Account original = new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        );

        Account updated = new Account(
                1,
                "afonso",
                "hash456",
                Set.of(Role.ADMIN),
                false,
                false
        );

        store.save(original);
        store.save(updated);

        Account found = store.findById(1).orElseThrow();

        assertEquals("hash456", found.getPasswordHash());
        assertEquals(Set.of(Role.ADMIN), found.getRoles());
        assertFalse(found.isActive());
        assertFalse(found.mustChangePassword());
        assertEquals(1, store.findAll().size());
    }

    @Test
    void findActiveReturnsOnlyActiveAccounts() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(1, "active", "hash1", Set.of(Role.USER), true));
        store.save(new Account(2, "disabled", "hash2", Set.of(Role.USER), false));

        assertEquals(1, store.findActive().size());
        assertEquals("active", store.findActive().get(0).getUsername());
    }

    @Test
    void findDisabledReturnsOnlyDisabledAccounts() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(1, "active", "hash1", Set.of(Role.USER), true));
        store.save(new Account(2, "disabled", "hash2", Set.of(Role.USER), false));

        assertEquals(1, store.findDisabled().size());
        assertEquals("disabled", store.findDisabled().get(0).getUsername());
    }

    @Test
    void queryMethodsReturnMutableListsConsistently() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(1, "active", "hash1", Set.of(Role.USER), true));
        store.save(new Account(2, "disabled", "hash2", Set.of(Role.USER), false));

        assertDoesNotThrow(() -> store.findAll().add(
                new Account(3, "other", "hash3", Set.of(Role.USER), true)
        ));
        assertDoesNotThrow(() -> store.findActive().add(
                new Account(3, "other", "hash3", Set.of(Role.USER), true)
        ));
        assertDoesNotThrow(() -> store.findDisabled().add(
                new Account(4, "other-disabled", "hash4", Set.of(Role.USER), false)
        ));
    }

    @Test
    void missingFileReturnsEmptyResults() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        assertTrue(store.findAll().isEmpty());
        assertTrue(store.findActive().isEmpty());
        assertTrue(store.findDisabled().isEmpty());
        assertTrue(store.findByUsername("unknown").isEmpty());
        assertTrue(store.findById(999).isEmpty());
    }

    @Test
    void accountsAreLoadedAfterCreatingNewStoreInstance() {
        Path accountsPath = tempDir.resolve("accounts.json");

        AccountStore firstStore = new AccountStore(accountsPath);

        firstStore.save(new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        ));

        AccountStore secondStore = new AccountStore(accountsPath);

        assertTrue(secondStore.findByUsername("afonso").isPresent());
        assertEquals(1, secondStore.findAll().size());
    }

    @Test
    void jsonDoesNotContainPlainPasswordField() throws Exception {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        ));

        String json = Files.readString(accountsPath);

        assertTrue(json.contains("passwordHash"));
        assertFalse(json.contains("\"password\""));
        assertFalse(json.contains("plainPassword"));
        assertFalse(json.contains("temporaryPassword"));
    }

    @Test
    void getNextIdReturnsOneWhenThereAreNoAccounts() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        assertEquals(1, store.getNextId());
    }

    @Test
    void getNextIdReturnsMaxIdPlusOne() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(1, "user1", "hash1", Set.of(Role.USER), true));
        store.save(new Account(2, "user2", "hash2", Set.of(Role.USER), false));
        store.save(new Account(10, "user10", "hash10", Set.of(Role.ADMIN), true));

        assertEquals(11, store.getNextId());
    }

    @Test
    void corruptedJsonShouldThrowAccountStoreException() throws Exception {
        Path accountsPath = tempDir.resolve("accounts.json");
        Files.writeString(accountsPath, "{ invalid json");

        AccountStore store = new AccountStore(accountsPath);

        assertThrows(AccountStoreException.class, store::findAll);
    }

    @Test
    void saveCreatesMissingParentDirectories() {
        Path accountsPath = tempDir
                .resolve("nested")
                .resolve("data")
                .resolve("accounts.json");

        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        ));

        assertTrue(Files.exists(accountsPath));
        assertTrue(Files.exists(accountsPath.getParent()));
    }

    @Test
    void saveNullAccountThrowsException() {
        Path accountsPath = tempDir.resolve("accounts.json");
        AccountStore store = new AccountStore(accountsPath);

        assertThrows(
                NullPointerException.class,
                () -> store.save(null)
        );
    }

    @Test
    void saveDoesNotLeaveTemporaryFileBehind() {
        Path accountsPath = tempDir.resolve("accounts.json");
        Path tempPath = tempDir.resolve("accounts.json.tmp");

        AccountStore store = new AccountStore(accountsPath);

        store.save(new Account(
                1,
                "afonso",
                "hash123",
                Set.of(Role.USER),
                true
        ));

        assertTrue(Files.exists(accountsPath));
        assertFalse(Files.exists(tempPath));
    }
}