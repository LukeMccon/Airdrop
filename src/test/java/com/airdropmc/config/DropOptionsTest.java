package com.airdropmc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DropOptionsTest {

    @Test
    void createDefault_returnsNewInstance() {
        DropOptions options = DropOptions.createDefault();

        assertNotNull(options);
    }

    @Test
    void createDefault_returnsDifferentInstancesEachTime() {
        DropOptions options1 = DropOptions.createDefault();
        DropOptions options2 = DropOptions.createDefault();

        assertNotSame(options1, options2);
    }

    // Builder fluency tests - verify each method returns 'this'

    @Test
    void withChickenCount_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withChickenCount(10);

        assertSame(options, result);
    }

    @Test
    void withFallingSpeed_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withFallingSpeed(0.5);

        assertSame(options, result);
    }

    @Test
    void withDropHeight_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withDropHeight(30);

        assertSame(options, result);
    }

    @Test
    void withLandingEffects_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withLandingEffects(true);

        assertSame(options, result);
    }

    @Test
    void withContinuousEffects_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withContinuousEffects(false);

        assertSame(options, result);
    }

    @Test
    void withFlareEffects_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withFlareEffects(true);

        assertSame(options, result);
    }

    @Test
    void withSmokeEnabled_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withSmokeEnabled(false);

        assertSame(options, result);
    }

    @Test
    void withSmokeHeight_returnsSameInstance() {
        DropOptions options = DropOptions.createDefault();

        DropOptions result = options.withSmokeHeight(25);

        assertSame(options, result);
    }

    // Getter tests with explicitly set values

    @Test
    void getChickenCount_returnsExplicitlySetValue() {
        DropOptions options = DropOptions.createDefault().withChickenCount(15);

        assertEquals(15, options.getChickenCount());
    }

    @Test
    void getFallingSpeed_returnsExplicitlySetValue() {
        DropOptions options = DropOptions.createDefault().withFallingSpeed(0.75);

        assertEquals(0.75, options.getFallingSpeed());
    }

    @Test
    void getDropHeight_returnsExplicitlySetValue() {
        DropOptions options = DropOptions.createDefault().withDropHeight(50);

        assertEquals(50, options.getDropHeight());
    }

    @Test
    void shouldShowLandingEffects_returnsExplicitlySetValue() {
        DropOptions optionsTrue = DropOptions.createDefault().withLandingEffects(true);
        DropOptions optionsFalse = DropOptions.createDefault().withLandingEffects(false);

        assertTrue(optionsTrue.shouldShowLandingEffects());
        assertFalse(optionsFalse.shouldShowLandingEffects());
    }

    @Test
    void shouldShowContinuousEffects_returnsExplicitlySetValue() {
        DropOptions optionsTrue = DropOptions.createDefault().withContinuousEffects(true);
        DropOptions optionsFalse = DropOptions.createDefault().withContinuousEffects(false);

        assertTrue(optionsTrue.shouldShowContinuousEffects());
        assertFalse(optionsFalse.shouldShowContinuousEffects());
    }

    @Test
    void shouldShowFlareEffects_returnsExplicitlySetValue() {
        DropOptions optionsTrue = DropOptions.createDefault().withFlareEffects(true);
        DropOptions optionsFalse = DropOptions.createDefault().withFlareEffects(false);

        assertTrue(optionsTrue.shouldShowFlareEffects());
        assertFalse(optionsFalse.shouldShowFlareEffects());
    }

    @Test
    void isSmokeEnabled_returnsExplicitlySetValue() {
        DropOptions optionsTrue = DropOptions.createDefault().withSmokeEnabled(true);
        DropOptions optionsFalse = DropOptions.createDefault().withSmokeEnabled(false);

        assertTrue(optionsTrue.isSmokeEnabled());
        assertFalse(optionsFalse.isSmokeEnabled());
    }

    @Test
    void getSmokeHeight_returnsExplicitlySetValue() {
        DropOptions options = DropOptions.createDefault().withSmokeHeight(100);

        assertEquals(100, options.getSmokeHeight());
    }

    @Test
    void getChickenCount_rejectsUnsafeLargeExplicitValue() {
        DropOptions options = DropOptions.createDefault().withChickenCount(10000);

        assertEquals(5, options.getChickenCount());
    }

    @Test
    void getFallingSpeed_rejectsZeroExplicitValue() {
        DropOptions options = DropOptions.createDefault().withFallingSpeed(0.0);

        assertEquals(0.3, options.getFallingSpeed());
    }

    @Test
    void getDropHeight_rejectsNegativeExplicitValue() {
        DropOptions options = DropOptions.createDefault().withDropHeight(-5);

        assertEquals(100, options.getDropHeight());
    }

    @Test
    void getSmokeHeight_rejectsNegativeExplicitValue() {
        DropOptions options = DropOptions.createDefault().withSmokeHeight(-2);

        assertEquals(20, options.getSmokeHeight());
    }

    // Fluent chaining test

    @Test
    void builderChain_supportsFluentChaining() {
        DropOptions options = DropOptions.createDefault()
                .withChickenCount(8)
                .withFallingSpeed(0.4)
                .withDropHeight(40)
                .withLandingEffects(true)
                .withContinuousEffects(false)
                .withFlareEffects(true)
                .withSmokeEnabled(true)
                .withSmokeHeight(30);

        assertEquals(8, options.getChickenCount());
        assertEquals(0.4, options.getFallingSpeed());
        assertEquals(40, options.getDropHeight());
        assertTrue(options.shouldShowLandingEffects());
        assertFalse(options.shouldShowContinuousEffects());
        assertTrue(options.shouldShowFlareEffects());
        assertTrue(options.isSmokeEnabled());
        assertEquals(30, options.getSmokeHeight());
    }

    // Default fallback tests (with mocked ConfigKeys)

    @Test
    void getChickenCount_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::getParachuteChickenCount).thenReturn(7);

            DropOptions options = DropOptions.createDefault();

            assertEquals(7, options.getChickenCount());
        }
    }

    @Test
    void getFallingSpeed_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::getDropFallingSpeed).thenReturn(0.25);

            DropOptions options = DropOptions.createDefault();

            assertEquals(0.25, options.getFallingSpeed());
        }
    }

    @Test
    void getDropHeight_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::getDropHeight).thenReturn(25);

            DropOptions options = DropOptions.createDefault();

            assertEquals(25, options.getDropHeight());
        }
    }

    @Test
    void shouldShowLandingEffects_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::shouldShowLandingParticleEffects).thenReturn(false);

            DropOptions options = DropOptions.createDefault();

            assertFalse(options.shouldShowLandingEffects());
        }
    }

    @Test
    void isSmokeEnabled_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::isSmokeEnabled).thenReturn(false);

            DropOptions options = DropOptions.createDefault();

            assertFalse(options.isSmokeEnabled());
        }
    }

    @Test
    void getSmokeHeight_fallsBackToConfigKeys_whenNotSet() {
        try (MockedStatic<ConfigKeys> configKeysMock = Mockito.mockStatic(ConfigKeys.class)) {
            configKeysMock.when(ConfigKeys::getSmokeHeight).thenReturn(15);

            DropOptions options = DropOptions.createDefault();

            assertEquals(15, options.getSmokeHeight());
        }
    }
}
