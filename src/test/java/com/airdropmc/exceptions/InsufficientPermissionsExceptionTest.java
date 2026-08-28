package com.airdropmc.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InsufficientPermissionsExceptionTest {

    @Test
    void constructor_setsPackageName() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("legendary");

        assertEquals("legendary", exception.getPackageName());
    }

    @Test
    void getPackageName_returnsCorrectValue() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("common");

        assertEquals("common", exception.getPackageName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "premium", "legendary", "super-package", ""})
    void constructor_handlesVariousPackageNames(String packageName) {
        InsufficientPermissionsException exception = new InsufficientPermissionsException(packageName);

        assertEquals(packageName, exception.getPackageName());
    }

    @Test
    void isException_extendsException() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("test");

        assertInstanceOf(Exception.class, exception);
    }
}
