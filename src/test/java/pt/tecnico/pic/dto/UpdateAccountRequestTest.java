package pt.tecnico.pic.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.Role;

class UpdateAccountRequestTest {
    @Test
    void shouldCarryOnlyGeneralAccountData() {
        UpdateAccountRequest request = new UpdateAccountRequest(7, "alice", Set.of(Role.ADMIN, Role.USER), true);

        assertEquals(7, request.getAccountId());
        assertEquals("alice", request.getUsername());
        assertEquals(Set.of(Role.ADMIN, Role.USER), request.getRoles());
        assertEquals(true, request.isActive());
    }

    @Test
    void shouldNotExposePasswordPinOrKeyFields() {
        Set<String> fieldNames = Arrays.stream(UpdateAccountRequest.class.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> methodNames = Arrays.stream(UpdateAccountRequest.class.getDeclaredMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertFalse(containsSensitiveName(fieldNames));
        assertFalse(containsSensitiveName(methodNames));
    }

    private static boolean containsSensitiveName(Set<String> names) {
        return names.stream().anyMatch(name -> name.contains("password")
                || name.contains("pin")
                || name.contains("key")
                || name.contains("secret"));
    }
}
