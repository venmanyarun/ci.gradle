package io.openliberty.guides.moduleC;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for Module C.
 * This test must NOT run when only Module A's libertyDev is active.
 * Same rationale as ModuleBIT — presence of this test in the output indicates
 * the regression from GitHub issue #1098 is still present.
 */
public class ModuleCIT {

    @Test
    public void moduleCTestShouldNotRunFromModuleADevMode() {
        fail("Module C tests ran while only Module A's libertyDev was active. " +
             "The runIntegrationTests scope fix is not effective.");
    }
}
