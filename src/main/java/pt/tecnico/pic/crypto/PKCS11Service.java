package pt.tecnico.pic.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import java.util.Locale;
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
    private static final int FORMAT_VERSION = 2;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int MAX_IV_BYTES = 32;
    private static final int MAX_EXTENSION_BYTES = 255;
    private static final int FILE_BUFFER_BYTES = 64 * 1024;
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

        if (Files.isDirectory(output)) {
            return result(OperationResult.ERROR, "Output path cannot be a directory.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        if (Files.exists(output)) {
            return result(OperationResult.FAILED, "Output file already exists.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        }

        byte[] inputBuffer = new byte[FILE_BUFFER_BYTES];
        Path temporaryOutput = null;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            String extension = extractFileExtension(input);
            byte[] authenticatedHeader = buildAuthenticatedHeader(iv, extension);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(authenticatedHeader);

            temporaryOutput = createTemporaryOutput(output);
            try (InputStream inputStream = Files.newInputStream(input);
                 OutputStream outputStream = Files.newOutputStream(
                         temporaryOutput,
                         StandardOpenOption.TRUNCATE_EXISTING
                 )) {
                outputStream.write(authenticatedHeader);
                processCipherStream(inputStream, outputStream, cipher, inputBuffer);
            }

            moveCompletedOutput(temporaryOutput, output);
            temporaryOutput = null;

            return result(OperationResult.SUCCESS, "File encrypted successfully.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } catch (IOException ex) {
            return result(OperationResult.ERROR, "File encryption failed.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } catch (GeneralSecurityException | RuntimeException ex) {
            return result(OperationResult.ERROR, "File encryption failed.", inputPath, outputPath,
                    ActionType.ENCRYPT_FILE);
        } finally {
            Arrays.fill(inputBuffer, (byte) 0);
            deleteQuietly(temporaryOutput);
        }
    }

    @Override
    public synchronized CryptoResult decryptFile(String inputPath, String outputPath) {
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

        if (Files.isDirectory(output)) {
            return result(OperationResult.ERROR, "Output path cannot be a directory.", inputPath, outputPath,
                    ActionType.DECRYPT_FILE);
        }

        byte[] inputBuffer = new byte[FILE_BUFFER_BYTES];
        Path temporaryOutput = null;
        try (InputStream inputStream = Files.newInputStream(input)) {
            EncryptedFile encryptedFile = readEncryptedFile(inputStream, Files.size(input));
            Path finalOutput = applyOriginalExtension(output, encryptedFile.extension());

            if (Files.isDirectory(finalOutput)) {
                return result(OperationResult.ERROR, "Output path cannot be a directory.", inputPath,
                        finalOutput.toString(), ActionType.DECRYPT_FILE);
            }

            if (Files.exists(finalOutput)) {
                return result(OperationResult.FAILED, "Output file already exists.", inputPath, finalOutput.toString(),
                        ActionType.DECRYPT_FILE);
            }

            if (isSameNormalizedPath(input, finalOutput)) {
                return result(OperationResult.FAILED, "Input and output files must be different.", inputPath,
                        finalOutput.toString(), ActionType.DECRYPT_FILE);
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, encryptedFile.iv()));
            cipher.updateAAD(encryptedFile.authenticatedHeader());

            temporaryOutput = createTemporaryOutput(finalOutput);
            try (OutputStream outputStream = Files.newOutputStream(
                    temporaryOutput,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                processCipherStream(inputStream, outputStream, cipher, inputBuffer);
            }

            moveCompletedOutput(temporaryOutput, finalOutput);
            temporaryOutput = null;

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
            Arrays.fill(inputBuffer, (byte) 0);
            deleteQuietly(temporaryOutput);
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

    private static EncryptedFile readEncryptedFile(InputStream input, long fileSize)
            throws IOException, InvalidEncryptedFileException {
        int fixedHeaderLength = MAGIC.length + 3;
        byte[] fixedHeader = input.readNBytes(fixedHeaderLength);

        if (fixedHeader.length != fixedHeaderLength || fileSize <= fixedHeaderLength) {
            throw new InvalidEncryptedFileException();
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (fixedHeader[i] != MAGIC[i]) {
                throw new InvalidEncryptedFileException();
            }
        }

        int version = Byte.toUnsignedInt(fixedHeader[MAGIC.length]);
        int ivLength = Byte.toUnsignedInt(fixedHeader[MAGIC.length + 1]);
        int extensionLength = Byte.toUnsignedInt(fixedHeader[MAGIC.length + 2]);
        int authenticatedHeaderLength = fixedHeaderLength + ivLength + extensionLength;

        if (version != FORMAT_VERSION
                || ivLength <= 0
                || ivLength > MAX_IV_BYTES
                || extensionLength > MAX_EXTENSION_BYTES
                || fileSize <= authenticatedHeaderLength) {
            throw new InvalidEncryptedFileException();
        }

        byte[] variableHeader = input.readNBytes(ivLength + extensionLength);
        if (variableHeader.length != ivLength + extensionLength) {
            throw new InvalidEncryptedFileException();
        }

        byte[] iv = Arrays.copyOfRange(variableHeader, 0, ivLength);
        byte[] extensionBytes = Arrays.copyOfRange(variableHeader, ivLength, variableHeader.length);
        String extension = new String(extensionBytes, StandardCharsets.UTF_8);
        try {
            validateExtension(extension);
        } catch (IllegalArgumentException ex) {
            throw new InvalidEncryptedFileException();
        }

        byte[] authenticatedHeader = new byte[authenticatedHeaderLength];
        System.arraycopy(fixedHeader, 0, authenticatedHeader, 0, fixedHeader.length);
        System.arraycopy(variableHeader, 0, authenticatedHeader, fixedHeader.length, variableHeader.length);
        return new EncryptedFile(iv, extension, authenticatedHeader);
    }

    private static byte[] buildAuthenticatedHeader(byte[] iv, String extension) throws IOException {
        validateExtension(extension);
        byte[] extensionBytes = extension.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(MAGIC);
        header.write(FORMAT_VERSION);
        header.write(iv.length);
        header.write(extensionBytes.length);
        header.write(iv);
        header.write(extensionBytes);
        return header.toByteArray();
    }

    private static String extractFileExtension(Path input) {
        String fileName = input.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(lastDot);
        validateExtension(extension);
        return extension;
    }

    private static void validateExtension(String extension) {
        if (extension == null || extension.getBytes(StandardCharsets.UTF_8).length > MAX_EXTENSION_BYTES) {
            throw new IllegalArgumentException("File extension is invalid.");
        }

        if (extension.isEmpty()) {
            return;
        }

        if (!extension.startsWith(".") || extension.length() == 1) {
            throw new IllegalArgumentException("File extension is invalid.");
        }

        for (int i = 0; i < extension.length(); i++) {
            char character = extension.charAt(i);
            if (character == '/' || character == '\\' || character == '\0' || Character.isISOControl(character)) {
                throw new IllegalArgumentException("File extension is invalid.");
            }
        }
    }

    private static Path applyOriginalExtension(Path requestedOutput, String extension) {
        String requestedName = requestedOutput.getFileName().toString();
        if (!extension.isEmpty()
                && requestedName.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
            return requestedOutput;
        }

        int lastDot = requestedName.lastIndexOf('.');
        String baseName = lastDot <= 0 ? requestedName : requestedName.substring(0, lastDot);
        return requestedOutput.resolveSibling(baseName + extension);
    }

    private static void writePlainFile(Path output, byte[] bytes) throws IOException {
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, bytes);
    }

    private static void processCipherStream(
            InputStream input,
            OutputStream output,
            Cipher cipher,
            byte[] inputBuffer
    ) throws IOException, GeneralSecurityException {
        int bytesRead;
        while ((bytesRead = input.read(inputBuffer)) != -1) {
            byte[] processedBytes = cipher.update(inputBuffer, 0, bytesRead);
            if (processedBytes != null) {
                try {
                    if (processedBytes.length > 0) {
                        output.write(processedBytes);
                    }
                } finally {
                    Arrays.fill(processedBytes, (byte) 0);
                }
            }
        }

        byte[] finalBytes = cipher.doFinal();
        try {
            if (finalBytes.length > 0) {
                output.write(finalBytes);
            }
        } finally {
            Arrays.fill(finalBytes, (byte) 0);
        }
    }

    private static Path createTemporaryOutput(Path output) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || absoluteOutput.getFileName() == null) {
            throw new IOException("Output path is invalid.");
        }

        Files.createDirectories(parent);
        String prefix = absoluteOutput.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = (prefix + "___").substring(0, 3);
        }
        return Files.createTempFile(parent, prefix, ".tmp");
    }

    private static void moveCompletedOutput(Path temporaryOutput, Path output) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Files.move(temporaryOutput, absoluteOutput);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup of incomplete output.
        }
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

    private record EncryptedFile(
            byte[] iv,
            String extension,
            byte[] authenticatedHeader
    ) {
    }

    private static class InvalidEncryptedFileException extends Exception {
    }
}
