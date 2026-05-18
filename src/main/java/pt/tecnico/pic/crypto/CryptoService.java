package pt.tecnico.pic.crypto;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;

public interface CryptoService {

    void initialize();

    OperationResult openSession(char[] pin);

    OperationResult closeSession();

    boolean isSessionOpen();

    CryptoResult encryptFile(String inputPath, String outputPath);

    CryptoResult decryptFile(String inputPath, String outputPath);
}
