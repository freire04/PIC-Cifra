package pt.tecnico.pic.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AuthProvider;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;

class PKCS11ServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeShouldFailWhenConfigFileDoesNotExist() {
        PKCS11Service service = new PKCS11Service(tempDir.resolve("missing-pkcs11.cfg"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::initialize);

        assertEquals("PKCS#11 configuration file not found.", ex.getMessage());
    }

    @Test
    void constructorShouldRejectInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new PKCS11Service(null, "alias"));
        assertThrows(IllegalArgumentException.class, () -> new PKCS11Service(tempDir.resolve("pkcs11.cfg"), " "));
    }

    @Test
    void defaultConstructorShouldUseConfigPathFromSystemProperty() throws Exception {
        String previousConfig = System.getProperty("pic.pkcs11.config");
        Path configuredPath = tempDir.resolve("custom-pkcs11.cfg");

        try {
            System.setProperty("pic.pkcs11.config", configuredPath.toString());

            PKCS11Service service = new PKCS11Service();

            assertEquals(configuredPath, getField(service, "configPath"));
        } finally {
            if (previousConfig == null) {
                System.clearProperty("pic.pkcs11.config");
            } else {
                System.setProperty("pic.pkcs11.config", previousConfig);
            }
        }
    }

    @Test
    void initializeShouldUseConfiguredProviderWhenNoProviderWithConfiguredNameExists() throws Exception {
        Path config = tempDir.resolve("pkcs11.cfg");
        Files.writeString(config, "name = Test");
        Provider originalProvider = Security.getProvider("SunPKCS11");
        ConfigurableProvider baseProvider = new ConfigurableProvider("ConfiguredPKCS11");
        Security.removeProvider("SunPKCS11");
        Security.removeProvider("ConfiguredPKCS11");

        try {
            Security.addProvider(baseProvider);
            PKCS11Service service = new PKCS11Service(config);

            service.initialize();
            service.initialize();

            assertTrue(baseProvider.configured);
            assertNotNull(Security.getProvider("ConfiguredPKCS11"));
        } finally {
            Security.removeProvider("SunPKCS11");
            Security.removeProvider("ConfiguredPKCS11");
            if (originalProvider != null) {
                Security.addProvider(originalProvider);
            }
        }
    }

    @Test
    void initializeShouldReuseExistingConfiguredProvider() throws Exception {
        Path config = tempDir.resolve("pkcs11.cfg");
        Files.writeString(config, "name = Existing");
        Provider originalProvider = Security.getProvider("SunPKCS11");
        ConfigurableProvider baseProvider = new ConfigurableProvider("ExistingConfiguredPKCS11");
        Provider existingConfiguredProvider = new Provider("ExistingConfiguredPKCS11", "1.0", "existing") {
        };
        Security.removeProvider("SunPKCS11");
        Security.removeProvider("ExistingConfiguredPKCS11");

        try {
            Security.addProvider(baseProvider);
            Security.addProvider(existingConfiguredProvider);
            PKCS11Service service = new PKCS11Service(config);

            service.initialize();

            assertTrue(baseProvider.configured);
            assertEquals(existingConfiguredProvider, getField(service, "provider"));
        } finally {
            Security.removeProvider("SunPKCS11");
            Security.removeProvider("ExistingConfiguredPKCS11");
            if (originalProvider != null) {
                Security.addProvider(originalProvider);
            }
        }
    }

    @Test
    void initializeShouldFailWhenSunPkcs11ProviderIsUnavailable() throws Exception {
        Path config = tempDir.resolve("pkcs11.cfg");
        Files.writeString(config, "name = Missing");
        Provider originalProvider = Security.getProvider("SunPKCS11");
        Security.removeProvider("SunPKCS11");

        try {
            PKCS11Service service = new PKCS11Service(config);

            IllegalStateException ex = assertThrows(IllegalStateException.class, service::initialize);

            assertEquals("SunPKCS11 provider is not available in this JDK.", ex.getMessage());
        } finally {
            if (originalProvider != null) {
                Security.addProvider(originalProvider);
            }
        }
    }

    @Test
    void initializeShouldWrapProviderConfigurationFailure() throws Exception {
        Path config = tempDir.resolve("pkcs11.cfg");
        Files.writeString(config, "name = Broken");
        Provider originalProvider = Security.getProvider("SunPKCS11");
        Security.removeProvider("SunPKCS11");

        try {
            Security.addProvider(new FailingConfigurableProvider());
            PKCS11Service service = new PKCS11Service(config);

            IllegalStateException ex = assertThrows(IllegalStateException.class, service::initialize);

            assertEquals("Could not initialize the PKCS#11 provider.", ex.getMessage());
            assertEquals(InvalidParameterException.class, ex.getCause().getClass());
        } finally {
            Security.removeProvider("SunPKCS11");
            if (originalProvider != null) {
                Security.addProvider(originalProvider);
            }
        }
    }

    @Test
    void openSessionShouldFailWithNullOrEmptyPin() {
        PKCS11Service service = new PKCS11Service();

        assertEquals(OperationResult.FAILED, service.openSession(null));
        assertEquals(OperationResult.FAILED, service.openSession(new char[0]));
    }

    @Test
    void openSessionShouldReturnErrorWhenInitializationFails() {
        PKCS11Service service = new PKCS11Service(tempDir.resolve("missing-pkcs11.cfg"));

        assertEquals(OperationResult.ERROR, service.openSession("1234".toCharArray()));
        assertFalse(service.isSessionOpen());
    }

    @Test
    void openSessionShouldSucceedWithLoadedSecretKey() throws Exception {
        PKCS11Service service = new PKCS11Service();
        setInitializedProvider(service, new KeyStoreProvider(false));

        assertEquals(OperationResult.SUCCESS, service.openSession("1234".toCharArray()));

        assertTrue(service.isSessionOpen());
    }

    @Test
    void openSessionShouldFailWhenTokenStoreCannotLoad() throws Exception {
        PKCS11Service service = new PKCS11Service();
        setInitializedProvider(service, new KeyStoreProvider(true));

        assertEquals(OperationResult.FAILED, service.openSession("1234".toCharArray()));

        assertFalse(service.isSessionOpen());
    }

    @Test
    void closeSessionShouldSucceedWithoutOpenSession() {
        PKCS11Service service = new PKCS11Service();

        assertEquals(OperationResult.SUCCESS, service.closeSession());
        assertFalse(service.isSessionOpen());
    }

    @Test
    void isSessionOpenShouldRequireCompleteSessionState() throws Exception {
        PKCS11Service service = new PKCS11Service();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        setField(service, "sessionOpen", true);
        setField(service, "keyStore", null);
        setField(service, "secretKey", new SecretKeySpec(new byte[16], "AES"));
        assertFalse(service.isSessionOpen());

        setField(service, "keyStore", keyStore);
        setField(service, "secretKey", null);
        assertFalse(service.isSessionOpen());
    }

    @Test
    void fileOperationsShouldFailWithoutOpenSession() {
        PKCS11Service service = new PKCS11Service();

        assertFalse(service.isSessionOpen());
        assertEquals(OperationResult.FAILED, service.encryptFile("plain.txt", "plain.cif").getResult());
        assertEquals(OperationResult.FAILED, service.decryptFile("plain.cif", "plain.txt").getResult());
    }

    @Test
    void encryptFileShouldRejectBlankPaths() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        assertEquals(OperationResult.FAILED, service.encryptFile("", "out.pic").getResult());
        assertEquals(OperationResult.FAILED, service.encryptFile("plain.txt", " ").getResult());
    }

    @Test
    void decryptFileShouldRejectBlankPaths() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        assertEquals(OperationResult.FAILED, service.decryptFile("", "plain.txt").getResult());
        assertEquals(OperationResult.FAILED, service.decryptFile("plain.pic", " ").getResult());
    }

    @Test
    void fileOperationsShouldRejectInvalidPaths() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        assertEquals(OperationResult.FAILED, service.encryptFile("bad\0path", "out.pic").getResult());
        assertEquals(OperationResult.FAILED, service.decryptFile("in.pic", "bad\0path").getResult());
    }

    @Test
    void encryptFileShouldRejectSameInputAndOutputBeforeReadingFile() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        String filePath = tempDir.resolve("same.txt").toString();

        CryptoResult result = service.encryptFile(filePath, filePath);

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Input and output files must be different.", result.getMessage());
    }

    @Test
    void decryptFileShouldRejectSameInputAndOutputBeforeReadingFile() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        String filePath = tempDir.resolve("same.pic").toString();

        CryptoResult result = service.decryptFile(filePath, filePath);

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Input and output files must be different.", result.getMessage());
    }

    @Test
    void encryptFileShouldReturnErrorWhenInputFileDoesNotExist() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        CryptoResult result = service.encryptFile(
                tempDir.resolve("missing.txt").toString(),
                tempDir.resolve("missing.pic").toString()
        );

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void decryptFileShouldReturnErrorWhenInputFileDoesNotExist() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        CryptoResult result = service.decryptFile(
                tempDir.resolve("missing.pic").toString(),
                tempDir.resolve("missing.txt").toString()
        );

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void decryptFileShouldFailWhenEncryptedFileIsInvalid() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("invalid.pic");
        Files.writeString(invalidEncryptedFile, "not a PIC encrypted file");

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void decryptFileShouldFailWhenEncryptedFileHasInvalidFormat() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("invalid-format.pic");
        byte[] invalidBytes = "PICPKCS11".getBytes(StandardCharsets.US_ASCII);
        invalidBytes = Arrays.copyOf(invalidBytes, invalidBytes.length + 14);
        invalidBytes[9] = 2;
        invalidBytes[10] = 12;
        Files.write(invalidEncryptedFile, invalidBytes);

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void encryptAndDecryptFileShouldRestoreOriginalContent() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service);
        Path input = tempDir.resolve("plain.txt");
        Path encrypted = tempDir.resolve("plain.pic");
        Path decrypted = tempDir.resolve("plain.out.txt");
        byte[] original = "conteudo a proteger".getBytes(StandardCharsets.UTF_8);
        Files.write(input, original);

        assertEquals(OperationResult.SUCCESS, service.encryptFile(input.toString(), encrypted.toString()).getResult());
        assertEquals(OperationResult.SUCCESS, service.decryptFile(encrypted.toString(), decrypted.toString()).getResult());

        assertArrayEquals(original, Files.readAllBytes(decrypted));
    }

    @Test
    void encryptingSameFileTwiceShouldProduceDifferentOutputs() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service);
        Path input = tempDir.resolve("plain.txt");
        Path firstEncrypted = tempDir.resolve("plain-1.pic");
        Path secondEncrypted = tempDir.resolve("plain-2.pic");
        Files.writeString(input, "mesmo conteudo");

        assertEquals(OperationResult.SUCCESS,
                service.encryptFile(input.toString(), firstEncrypted.toString()).getResult());
        assertEquals(OperationResult.SUCCESS,
                service.encryptFile(input.toString(), secondEncrypted.toString()).getResult());

        assertNotEquals(
                Arrays.toString(Files.readAllBytes(firstEncrypted)),
                Arrays.toString(Files.readAllBytes(secondEncrypted))
        );
    }

    @Test
    void decryptFileShouldFailWhenCiphertextIsTampered() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service);
        Path input = tempDir.resolve("plain.txt");
        Path encrypted = tempDir.resolve("plain.pic");
        Path decrypted = tempDir.resolve("plain.out.txt");
        Files.writeString(input, "conteudo autentico");

        assertEquals(OperationResult.SUCCESS, service.encryptFile(input.toString(), encrypted.toString()).getResult());
        byte[] encryptedBytes = Files.readAllBytes(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 1;
        Files.write(encrypted, encryptedBytes);

        CryptoResult result = service.decryptFile(encrypted.toString(), decrypted.toString());

        assertEquals(OperationResult.FAILED, result.getResult());
        assertFalse(Files.exists(decrypted));
    }

    @Test
    void closeSessionShouldSucceedAfterSessionWasOpened() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);

        assertTrue(service.isSessionOpen());
        assertEquals(OperationResult.SUCCESS, service.closeSession());
        assertFalse(service.isSessionOpen());
    }

    @Test
    void closeSessionShouldLogoutAuthProvider() throws Exception {
        PKCS11Service service = new PKCS11Service();
        StubAuthProvider provider = new StubAuthProvider(false);
        openFakeSession(service);
        setField(service, "provider", provider);

        assertEquals(OperationResult.SUCCESS, service.closeSession());

        assertTrue(provider.loggedOut);
        assertFalse(service.isSessionOpen());
    }

    @Test
    void closeSessionShouldReturnFailedWhenAuthProviderLogoutFails() throws Exception {
        PKCS11Service service = new PKCS11Service();
        StubAuthProvider provider = new StubAuthProvider(true);
        openFakeSession(service);
        setField(service, "provider", provider);

        assertEquals(OperationResult.FAILED, service.closeSession());

        assertTrue(provider.loggedOut);
        assertFalse(service.isSessionOpen());
    }

    @Test
    void encryptFileShouldReturnErrorWhenOutputIsDirectory() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service);
        Path input = tempDir.resolve("plain.txt");
        Path outputDirectory = tempDir.resolve("encrypted-output");
        Files.writeString(input, "conteudo");
        Files.createDirectory(outputDirectory);

        CryptoResult result = service.encryptFile(input.toString(), outputDirectory.toString());

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void decryptFileShouldReturnErrorWhenOutputIsDirectory() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service);
        Path input = tempDir.resolve("plain.txt");
        Path encrypted = tempDir.resolve("plain.pic");
        Path outputDirectory = tempDir.resolve("decrypted-output");
        Files.writeString(input, "conteudo");
        Files.createDirectory(outputDirectory);
        assertEquals(OperationResult.SUCCESS, service.encryptFile(input.toString(), encrypted.toString()).getResult());

        CryptoResult result = service.decryptFile(encrypted.toString(), outputDirectory.toString());

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void encryptFileShouldReturnErrorWhenCipherCannotInitialize() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openSoftwareBackedSession(service, new SecretKeySpec(new byte[8], "DES"));
        Path input = tempDir.resolve("plain.txt");
        Path encrypted = tempDir.resolve("plain.pic");
        Files.writeString(input, "conteudo");

        CryptoResult result = service.encryptFile(input.toString(), encrypted.toString());

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void decryptFileShouldReturnErrorWhenCipherCannotInitialize() throws Exception {
        PKCS11Service encryptingService = new PKCS11Service();
        openSoftwareBackedSession(encryptingService);
        Path input = tempDir.resolve("plain.txt");
        Path encrypted = tempDir.resolve("plain.pic");
        Path decrypted = tempDir.resolve("plain.out.txt");
        Files.writeString(input, "conteudo");
        assertEquals(OperationResult.SUCCESS, encryptingService.encryptFile(input.toString(), encrypted.toString()).getResult());

        PKCS11Service decryptingService = new PKCS11Service();
        openSoftwareBackedSession(decryptingService, new SecretKeySpec(new byte[8], "DES"));

        CryptoResult result = decryptingService.decryptFile(encrypted.toString(), decrypted.toString());

        assertEquals(OperationResult.ERROR, result.getResult());
    }

    @Test
    void decryptFileShouldFailWhenEncryptedFileIsTooShort() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("short.pic");
        Files.write(invalidEncryptedFile, "PICPKCS11".getBytes(StandardCharsets.US_ASCII));

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void decryptFileShouldFailWhenIvLengthIsInvalid() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("invalid-iv.pic");
        byte[] invalidBytes = "PICPKCS11".getBytes(StandardCharsets.US_ASCII);
        invalidBytes = Arrays.copyOf(invalidBytes, invalidBytes.length + 3);
        invalidBytes[9] = 1;
        invalidBytes[10] = 0;
        invalidBytes[11] = 1;
        Files.write(invalidEncryptedFile, invalidBytes);

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void decryptFileShouldFailWhenIvLengthExceedsMaximum() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("oversized-iv.pic");
        byte[] invalidBytes = Arrays.copyOf("PICPKCS11".getBytes(StandardCharsets.US_ASCII), 12);
        invalidBytes[9] = 1;
        invalidBytes[10] = 33;
        invalidBytes[11] = 1;
        Files.write(invalidEncryptedFile, invalidBytes);

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void decryptFileShouldFailWhenCiphertextIsMissing() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        Path invalidEncryptedFile = tempDir.resolve("missing-ciphertext.pic");
        byte[] invalidBytes = Arrays.copyOf("PICPKCS11".getBytes(StandardCharsets.US_ASCII), 23);
        invalidBytes[9] = 1;
        invalidBytes[10] = 12;
        Files.write(invalidEncryptedFile, invalidBytes);

        CryptoResult result = service.decryptFile(
                invalidEncryptedFile.toString(),
                tempDir.resolve("out.txt").toString()
        );

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Invalid encrypted file.", result.getMessage());
    }

    @Test
    void writePlainFileShouldHandleRootPathWithoutParent() {
        assertThrows(IOException.class, () -> invokeWritePlainFile(tempDir.getRoot(), new byte[] {1}));
    }

    @Test
    void destroyQuietlyShouldIgnoreDestroyFailures() throws Exception {
        invokeDestroyQuietly(new FailingPasswordProtection());
    }

    @Test
    void normalizedPathComparisonShouldUseCaseSensitiveBranchOutsideWindows() throws Exception {
        String previousOsName = System.getProperty("os.name");

        try {
            System.setProperty("os.name", "Linux");

            assertTrue(invokeIsSameNormalizedPath(
                    tempDir.resolve("same.txt"),
                    tempDir.resolve(".").resolve("same.txt")
            ));
        } finally {
            if (previousOsName == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", previousOsName);
            }
        }
    }

    @Test
    void loadOrCreateKeyShouldReturnExistingSecretKey() throws Exception {
        PKCS11Service service = new PKCS11Service(tempDir.resolve("pkcs11.cfg"), "existing-key");
        openSoftwareBackedSession(service);
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, null);
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.US_ASCII), "AES");
        keyStore.setEntry("existing-key", new KeyStore.SecretKeyEntry(key),
                new KeyStore.PasswordProtection("1234".toCharArray()));

        Object loadedKey = invokeLoadOrCreateKey(service, keyStore, "1234".toCharArray());

        assertEquals("AES", ((SecretKeySpec) loadedKey).getAlgorithm());
    }

    @Test
    void loadOrCreateKeyShouldCreateSecretKeyWhenAliasDoesNotExist() throws Exception {
        PKCS11Service service = new PKCS11Service(tempDir.resolve("pkcs11.cfg"), "new-key");
        openSoftwareBackedSession(service);
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, null);

        Object loadedKey = invokeLoadOrCreateKey(service, keyStore, "1234".toCharArray());

        assertNotNull(loadedKey);
        assertTrue(keyStore.containsAlias("new-key"));
    }

    @Test
    void loadOrCreateKeyShouldRejectNonSecretKeyAlias() throws Exception {
        PKCS11Service service = new PKCS11Service(tempDir.resolve("pkcs11.cfg"), "non-secret-key");
        openSoftwareBackedSession(service);
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, null);
        keyStore.setCertificateEntry("non-secret-key", TestCertificate.INSTANCE);

        assertThrows(KeyStoreException.class, () -> invokeLoadOrCreateKey(service, keyStore, "1234".toCharArray()));
    }

    private static void openFakeSession(PKCS11Service service) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        setField(service, "keyStore", keyStore);
        setField(service, "secretKey", new SecretKeySpec(new byte[16], "AES"));
        setField(service, "sessionOpen", true);
    }

    private static void openSoftwareBackedSession(PKCS11Service service) throws Exception {
        openSoftwareBackedSession(
                service,
                new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.US_ASCII), "AES")
        );
    }

    private static void openSoftwareBackedSession(PKCS11Service service, SecretKeySpec key) throws Exception {
        openFakeSession(service);
        Provider provider = Security.getProvider("SunJCE");
        setField(service, "provider", provider);
        setField(service, "secretKey", key);
    }

    private static Object invokeLoadOrCreateKey(PKCS11Service service, KeyStore keyStore, char[] pin) throws Exception {
        var method = PKCS11Service.class.getDeclaredMethod("loadOrCreateKey", KeyStore.class, char[].class);
        method.setAccessible(true);

        try {
            return method.invoke(service, keyStore, pin);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    private static void invokeWritePlainFile(Path output, byte[] bytes) throws Exception {
        var method = PKCS11Service.class.getDeclaredMethod("writePlainFile", Path.class, byte[].class);
        method.setAccessible(true);

        try {
            method.invoke(null, output, bytes);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    private static void invokeDestroyQuietly(KeyStore.PasswordProtection protection) throws Exception {
        var method = PKCS11Service.class.getDeclaredMethod("destroyQuietly", KeyStore.PasswordProtection.class);
        method.setAccessible(true);
        method.invoke(null, protection);
    }

    private static boolean invokeIsSameNormalizedPath(Path firstPath, Path secondPath) throws Exception {
        var method = PKCS11Service.class.getDeclaredMethod("isSameNormalizedPath", Path.class, Path.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, firstPath, secondPath);
    }

    private static void setField(PKCS11Service service, String fieldName, Object value) throws Exception {
        Field field = PKCS11Service.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static Object getField(PKCS11Service service, String fieldName) throws Exception {
        Field field = PKCS11Service.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(service);
    }

    private static void setInitializedProvider(PKCS11Service service, Provider provider) throws Exception {
        setField(service, "initialized", true);
        setField(service, "provider", provider);
    }

    private static class ConfigurableProvider extends Provider {
        private final String configuredProviderName;
        private boolean configured;

        ConfigurableProvider(String configuredProviderName) {
            super("SunPKCS11", "1.0", "configurable stub");
            this.configuredProviderName = configuredProviderName;
        }

        @Override
        public Provider configure(String configArg) {
            configured = true;
            return new Provider(configuredProviderName, "1.0", "configured stub") {
            };
        }
    }

    private static class FailingConfigurableProvider extends Provider {
        FailingConfigurableProvider() {
            super("SunPKCS11", "1.0", "failing configurable stub");
        }

        @Override
        public Provider configure(String configArg) {
            throw new InvalidParameterException("bad config");
        }
    }

    private static class KeyStoreProvider extends Provider {
        KeyStoreProvider(boolean failOnLoad) {
            super("KeyStoreProvider" + failOnLoad, "1.0", "keystore stub");
            TestKeyStoreSpi.failOnLoad = failOnLoad;
            put("KeyStore.PKCS11", TestKeyStoreSpi.class.getName());
        }
    }

    public static class TestKeyStoreSpi extends KeyStoreSpi {
        private static boolean failOnLoad;

        @Override
        public Key engineGetKey(String alias, char[] password) throws UnrecoverableKeyException {
            if ("pic-file-key".equals(alias)) {
                return new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.US_ASCII), "AES");
            }

            throw new UnrecoverableKeyException("unknown alias");
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            return null;
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            return null;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            return new Date();
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) {
        }

        @Override
        public void engineDeleteEntry(String alias) {
        }

        @Override
        public Enumeration<String> engineAliases() {
            return Collections.enumeration(Collections.singleton("pic-file-key"));
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return "pic-file-key".equals(alias);
        }

        @Override
        public int engineSize() {
            return 1;
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return engineContainsAlias(alias);
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            return null;
        }

        @Override
        public void engineStore(OutputStream stream, char[] password) {
        }

        @Override
        public void engineLoad(InputStream stream, char[] password) throws IOException {
            if (failOnLoad) {
                throw new IOException("load failed");
            }
        }
    }

    private static class StubAuthProvider extends AuthProvider {
        private final boolean failOnLogout;
        private boolean loggedOut;

        StubAuthProvider(boolean failOnLogout) {
            super("StubAuthProvider", "1.0", "Stub auth provider");
            this.failOnLogout = failOnLogout;
        }

        @Override
        public void login(Subject subject, CallbackHandler handler) {
        }

        @Override
        public void logout() throws LoginException {
            loggedOut = true;
            if (failOnLogout) {
                throw new LoginException("logout failed");
            }
        }

        @Override
        public void setCallbackHandler(CallbackHandler handler) {
        }
    }

    private static class FailingPasswordProtection extends KeyStore.PasswordProtection {
        FailingPasswordProtection() {
            super("1234".toCharArray());
        }

        @Override
        public synchronized void destroy() throws DestroyFailedException {
            throw new DestroyFailedException("destroy failed");
        }
    }

    private static class TestCertificate extends java.security.cert.Certificate {
        private static final TestCertificate INSTANCE = new TestCertificate();

        TestCertificate() {
            super("TEST");
        }

        @Override
        public byte[] getEncoded() {
            return new byte[] {1};
        }

        @Override
        public void verify(java.security.PublicKey key) {
        }

        @Override
        public void verify(java.security.PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "test certificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }
    }
}
