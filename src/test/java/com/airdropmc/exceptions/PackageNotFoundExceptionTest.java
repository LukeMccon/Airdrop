package com.airdropmc.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PackageNotFoundExceptionTest {

    @Test
    void constructor_setsPackageName() {
        PackageNotFoundException exception = new PackageNotFoundException("legendary");

        assertEquals("legendary", exception.getPackageName());
    }

    @Test
    void getPackageName_returnsCorrectValue() {
        PackageNotFoundException exception = new PackageNotFoundException("common");

        assertEquals("common", exception.getPackageName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "premium", "legendary", "super-package", ""})
    void constructor_handlesVariousPackageNames(String packageName) {
        PackageNotFoundException exception = new PackageNotFoundException(packageName);

        assertEquals(packageName, exception.getPackageName());
    }

    @Test
    void isException_extendsException() {
        PackageNotFoundException exception = new PackageNotFoundException("test");

        assertInstanceOf(Exception.class, exception);
    }
}
