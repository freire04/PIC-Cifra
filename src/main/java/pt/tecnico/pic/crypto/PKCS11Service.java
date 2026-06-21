package pt.tecnico.pic.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.AuthProvider;
import java.security.GeneralSecurityException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.login.LoginException;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;

public class PKCS11Service implements CryptoService {

    private static final String CONFIG_PROPERTY = "pic.pkcs11.config";
    private static final String CONFIG_ENV = "PIC_PKCS11_CONFIG";
    private static final String KEY_ALIAS_PROPERTY = "pic.pkcs11.keyAlias";
    private static final String DEFAULT_CONFIG_PATH = "pkcs11.cfg";
    private static final String DEFAULT_KEY_ALIAS = "pic-file-key";

    private static final byte[] MAGIC = "PICPKCS11".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int MAX_IV_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Path configPath;
    private final String keyAlias;

    private Provider provider;
    private KeyStore keyStore;
    private SecretKey secretKey;
    private boolean initialized;
    private boolean sessionOpen;

    public PKCS11Service() {
        this(resolveConfigPath(), resolveKeyAlias());
    }

    public PKCS11Service(Path configPath) {
        this(configPath, resolveKeyAlias());
    }

    public PKCS11Service(Path configPath, String keyAlias) {
        this.configPath = Objects.requireNonNull(configPath, "configPath must not be null");
        this.keyAlias = requireText(keyAlias, "keyAlias must not be blank");
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        Path absoluteConfigPath = configPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluteConfigPath)) {
            throw new IllegalStateException("PKCS#11 configuration file not found.");
        }

        Provider baseProvider = Security.getProvider("SunPKCS11");
        if (baseProvider == null) {
            throw new IllegalStateException("SunPKCS11 provider is not available in this JDK.");
        }

        try {
            Provider configuredProvider = baseProvider.configure(absoluteConfigPath.toString());
            Provider existingProvider = Security.getProvider(configuredProvider.getName());

            if (existingProvider == null) {
                Security.addProvider(configuredProvider);
                provider = configuredProvider;
            } else {
                provider = existingProvider;
            }

            initialized = true;
        } catch (InvalidParameterException | SecurityException ex) {
            throw new IllegalStateException("Could not initialize the PKCS#11 provider.", ex);
        }
    }

    @Override
    public synchronized OperationResult openSession(char[] pin) {
        if (pin == null || pin.length == 0) {
            return OperationResult.FAILED;
        }

        char[] pinCopy = pin.clone();
        try {
            initialize();

            KeyStore tokenStore = KeyStore.getInstance("PKCS11", provider);
            tokenStore.load(null, pinCopy);

            SecretKey tokenKey = loadOrCreateKey(tokenStore, pinCopy);
            keyStore = tokenStore;
            secretKey = tokenKey;
            sessionOpen = true;

            return OperationResult.SUCCESS;
        } catch (GeneralSecurityException | IOException ex) {
            clearSessionState();
            return OperationResult.FAILED;
        } catch (RuntimeException ex) {
            clearSessionState();
            return OperationResult.ERROR;
        } finally {
            Arrays.fill(pinCopy, '\0');
        }
    }

    @Override
    public synchronized OperationResult closeSession() {
        if (!sessionOpen) {
            clearSessionState();
            return OperationResult.SUCCESS;
        }

        try {
            if (provider instanceof AuthProvider authProvider) {
                authProvider.logout();
            }
            return OperationResult.SUCCESS;
        } catch (LoginException ex) {
            return OperationResult.FAILED;
        } finally {
            clearSessionState();
        }
    }

    @Override
    public synchronized boolean isSessionOpen() {
        return sessionOpen && keyStore != null && secretKey != null;
    }

    @Override
    public synchronized CryptoResult encryptFile(String inputPath, String outputPath) {
        if (!isSessionOpen()) {
            return result(OperationResult.FAILED, "Token session is not open.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        if (isBlank(inputPath) || isBlank(outputPath)) {
            return result(OperationResult.FAILED, "Input and output files are required.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        Path input;
        Path output;
        try {
            input = Path.of(inputPath);
            output = Path.of(outputPath);
        } catch (InvalidPathException ex) {
            return result(OperationResult.FAILED, "Input or output file path is invalid.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        if (isSameNormalizedPath(input, output)) {
            return result(OperationResult.FAILED, "Input and output files must be different.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        byte[] plaintext = null;
        try {
            plaintext = Files.readAllBytes(input);
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Obtém a extensão original (ex: ".txt")
            String filename = input.getFileName().toString();
            int dotIndex = filename.lastIndexOf('.');
            String extension = (dotIndex == -1) ? "" : filename.substring(dotIndex);

            writeEncryptedFile(output, iv, ciphertext, extension);

            return result(OperationResult.SUCCESS, "File encrypted successfully.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } catch (IOException ex) {
            return result(OperationResult.ERROR, "File encryption failed.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } catch (GeneralSecurityException | RuntimeException ex) {
            return result(OperationResult.ERROR, "File encryption failed.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    @Override
    public synchronized CryptoResult decryptFile(String inputPath, String outputPath) {
        java.io.File fileOutput = new java.io.File(outputPath);
        if (fileOutput.isDirectory()) {
                return result(OperationResult.ERROR, "Output path cannot be a directory.", inputPath, outputPath, ActionType.DECRYPT_FILE);
        }

        if (!isSessionOpen()) {
            return result(OperationResult.FAILED, "Token session is not open.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        }

        if (isBlank(inputPath) || isBlank(outputPath)) {
            return result(OperationResult.FAILED, "Input and output files are required.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        }

        Path input;
        Path output;
        try {
            input = Path.of(inputPath);
            output = Path.of(outputPath);
        } catch (InvalidPathException ex) {
            return result(OperationResult.FAILED, "Input or output file path is invalid.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        }

        if (isSameNormalizedPath(input, output)) {
            return result(OperationResult.FAILED, "Input and output files must be different.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        }

        byte[] plaintext = null;
        try {
            EncryptedFile encryptedFile = readEncryptedFile(input);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, encryptedFile.iv()));
            plaintext = cipher.doFinal(encryptedFile.ciphertext());

            // Garanta que o output herda a extensão original que foi guardada no cabeçalho
            Path finalOutput = output;
            String currentName = output.getFileName().toString();
            String targetExt = encryptedFile.extension();

            if (!targetExt.isEmpty() && !currentName.endsWith(targetExt)) {
                // Se o utilizador escreveu uma extensão diferente (ex: .dec), limpamos e forçamos a original (.txt)
                int lastDot = currentName.lastIndexOf('.');
                String baseName = (lastDot == -1) ? currentName : currentName.substring(0, lastDot);
                finalOutput = output.resolveSibling(baseName + targetExt);
            }

            writePlainFile(finalOutput, plaintext);

            return result(OperationResult.SUCCESS, "File decrypted successfully.", inputPath, finalOutput.toString(),
                    ActionType.DECRYPT_FILE);
        } catch (InvalidEncryptedFileException ex) {
            return result(OperationResult.FAILED, "Invalid encrypted file.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        } catch (AEADBadTagException ex) {
            return result(OperationResult.FAILED, "File decryption failed.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        } catch (IOException ex) {
            return result(OperationResult.ERROR, "File decryption failed.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        } catch (GeneralSecurityException | RuntimeException ex) {
            return result(OperationResult.ERROR, "File decryption failed.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    private SecretKey loadOrCreateKey(KeyStore tokenStore, char[] pin)
            throws GeneralSecurityException {
        if (tokenStore.containsAlias(keyAlias)) {
            Key key = tokenStore.getKey(keyAlias, pin);
            if (key instanceof SecretKey storedSecretKey) {
                return storedSecretKey;
            }

            throw new KeyStoreException("PKCS#11 key alias is not a secret key.");
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", provider);
        keyGenerator.init(AES_KEY_BITS);
        SecretKey generatedKey = keyGenerator.generateKey();

        KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(pin);
        try {
            tokenStore.setEntry(keyAlias, new KeyStore.SecretKeyEntry(generatedKey), protection);
        } finally {
            destroyQuietly(protection);
        }

        return generatedKey;
    }

    private static void writeEncryptedFile(Path output, byte[] iv, byte[] ciphertext, String extension) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(MAGIC);
        buffer.write(FORMAT_VERSION);
        buffer.write(iv.length);
        buffer.write(iv);

        // Grava o tamanho da extensão e os seus bytes
        byte[] extensionBytes = extension.getBytes(StandardCharsets.UTF_8);
        buffer.write(extensionBytes.length);
        buffer.write(extensionBytes);

        buffer.write(ciphertext);

        writePlainFile(output, buffer.toByteArray());
    }

    private static EncryptedFile readEncryptedFile(Path input) throws IOException, InvalidEncryptedFileException {
        byte[] bytes = Files.readAllBytes(input);
        int headerLength = MAGIC.length + 2;

        if (bytes.length <= headerLength) {
            throw new InvalidEncryptedFileException();
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                throw new InvalidEncryptedFileException();
            }
        }

        int version = Byte.toUnsignedInt(bytes[MAGIC.length]);
        int ivLength = Byte.toUnsignedInt(bytes[MAGIC.length + 1]);
        int currentOffset = headerLength;

        if (version != FORMAT_VERSION
                || ivLength <= 0
                || ivLength > MAX_IV_BYTES
                || bytes.length <= currentOffset + ivLength) {
            throw new InvalidEncryptedFileException();
        }

        byte[] iv = Arrays.copyOfRange(bytes, currentOffset, currentOffset + ivLength);
        currentOffset += ivLength;

        if (bytes.length <= currentOffset) {
            throw new InvalidEncryptedFileException();
        }

        // Lê a extensão guardada no cabeçalho
        int extensionLength = Byte.toUnsignedInt(bytes[currentOffset]);
        currentOffset += 1;

        if (bytes.length < currentOffset + extensionLength) {
            throw new InvalidEncryptedFileException();
        }

        byte[] extensionBytes = Arrays.copyOfRange(bytes, currentOffset, currentOffset + extensionLength);
        String extension = new String(extensionBytes, StandardCharsets.UTF_8);
        currentOffset += extensionLength;

        byte[] ciphertext = Arrays.copyOfRange(bytes, currentOffset, bytes.length);
        return new EncryptedFile(iv, ciphertext, extension);
    }

    private static void writePlainFile(Path output, byte[] bytes) throws IOException {
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, bytes);
    }

    private static CryptoResult result(OperationResult operationResult,
                                       String message,
                                       String inputPath,
                                       String outputPath,
                                       ActionType actionType) {
        return new CryptoResult(operationResult, message, inputPath, outputPath, actionType);
    }

    private static void destroyQuietly(KeyStore.PasswordProtection protection) {
        try {
            protection.destroy();
        } catch (DestroyFailedException ex) {
            // The PIN copy is still cleared by the caller.
        }
    }

    private static Path resolveConfigPath() {
        String configuredPath = System.getProperty(CONFIG_PROPERTY);
        if (isBlank(configuredPath)) {
            configuredPath = System.getenv(CONFIG_ENV);
        }

        return Path.of(isBlank(configuredPath) ? DEFAULT_CONFIG_PATH : configuredPath);
    }

    private static String resolveKeyAlias() {
        return System.getProperty(KEY_ALIAS_PROPERTY, DEFAULT_KEY_ALIAS);
    }

    private static String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isSameNormalizedPath(Path firstPath, Path secondPath) {
        Path normalizedFirstPath = firstPath.toAbsolutePath().normalize();
        Path normalizedSecondPath = secondPath.toAbsolutePath().normalize();

        if (isWindows()) {
            return normalizedFirstPath.toString().equalsIgnoreCase(normalizedSecondPath.toString());
        }

        return normalizedFirstPath.equals(normalizedSecondPath);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void clearSessionState() {
        keyStore = null;
        secretKey = null;
        sessionOpen = false;
    }

    private record EncryptedFile(byte[] iv, byte[] ciphertext, String extension) {
    }

    private static class InvalidEncryptedFileException extends Exception {
    }
}