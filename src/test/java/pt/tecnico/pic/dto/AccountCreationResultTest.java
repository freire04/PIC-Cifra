package pt.tecnico.pic.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.OperationResult;

class AccountCreationResultTest {
    @Test
    void temporaryPasswordShouldBeDefensivelyCopiedAndClearable() {
        char[] temporaryPassword = "TempPassword123!".toCharArray();
        AccountCreationResult result = new AccountCreationResult(
                OperationResult.SUCCESS,
                1,
                "admin",
                temporaryPassword,
                "ok"
        );

        char[] returnedPassword = result.getTemporaryPassword();
        returnedPassword[0] = 'X';

        assertArrayEquals(temporaryPassword, result.getTemporaryPassword());

        result.clearTemporaryPassword();

        assertNull(result.getTemporaryPassword());
    }
}
