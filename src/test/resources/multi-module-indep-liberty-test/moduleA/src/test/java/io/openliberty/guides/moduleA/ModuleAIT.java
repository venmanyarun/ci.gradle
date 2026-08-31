package io.openliberty.guides.moduleA;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placeholder integration test for Module A.
 * Dev mode triggers this when the user presses Enter (or hot tests are enabled).
 * This test must be the ONLY test that runs when :moduleA:libertyDev invokes tests.
 */
public class ModuleAIT {

    @Test
    public void moduleATestRuns() {
        // A trivial assertion so Gradle counts this as a passing test.
        assertTrue(true, "Module A integration test ran successfully");
    }
}
