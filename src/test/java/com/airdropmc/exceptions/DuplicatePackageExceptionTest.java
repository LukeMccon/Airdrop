package com.airdropmc.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DuplicatePackageExceptionTest {

    @Test
    void constructor_setsPackageName() {
        DuplicatePackageException exception = new DuplicatePackageException("legendary");

        assertEquals("legendary", exception.getPackageName());
    }

    @Test
    void getPackageName_returnsCorrectValue() {
        DuplicatePackageException exception = new DuplicatePackageException("common");

        assertEquals("common", exception.getPackageName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "premium", "legendary", "super-package", ""})
    void constructor_handlesVariousPackageNames(String packageName) {
        DuplicatePackageException exception = new DuplicatePackageException(packageName);

        assertEquals(packageName, exception.getPackageName());
    }

    @Test
    void isException_extendsException() {
        DuplicatePackageException exception = new DuplicatePackageException("test");

        assertInstanceOf(Exception.class, exception);
    }
}
