package pt.tecnico.pic.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.OperationResult;

class PasswordResultTest {
    @Test
    void temporaryPasswordShouldBeDefensivelyCopiedAndClearable() {
        char[] temporaryPassword = "Temporary123!".toCharArray();
        PasswordResult result = new PasswordResult(
                OperationResult.SUCCESS,
                "ok",
                temporaryPassword
        );

        char[] returnedPassword = result.getTemporaryPassword();
        returnedPassword[0] = 'X';

        assertArrayEquals(temporaryPassword, result.getTemporaryPassword());

        result.clearTemporaryPassword();

        assertNull(result.getTemporaryPassword());
    }
}
