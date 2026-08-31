package io.openliberty.guides.moduleB;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for Module B.
 * This test must NOT run when only Module A's libertyDev is active.
 * If this test runs it means the fix for GitHub issue #1098 is not working:
 * dev mode is triggering tests across all submodules instead of scoping
 * them to the module under dev mode.
 */
public class ModuleBIT {

    @Test
    public void moduleBTestShouldNotRunFromModuleADevMode() {
        // If this test is reached it means Module B's :test task was executed
        // while only Module A's libertyDev was running — the bug is present.
        fail("Module B tests ran while only Module A's libertyDev was active. " +
             "The runIntegrationTests scope fix is not effective.");
    }
}
