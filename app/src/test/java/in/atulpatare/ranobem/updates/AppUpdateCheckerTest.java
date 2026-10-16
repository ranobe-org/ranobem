package in.atulpatare.ranobem.updates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppUpdateCheckerTest {

    @Test
    public void newerTagIsAnUpdate() {
        assertTrue(AppUpdateChecker.isNewer("v16", "15"));
        assertTrue(AppUpdateChecker.isNewer("v15.1", "15"));
    }

    @Test
    public void comparesAsNumbersNotText() {
        assertTrue(AppUpdateChecker.isNewer("v10", "9"));
        assertFalse(AppUpdateChecker.isNewer("v9", "10"));
    }

    @Test
    public void sameOrOlderIsNot() {
        assertFalse(AppUpdateChecker.isNewer("v15", "15"));
        assertFalse(AppUpdateChecker.isNewer("v15.0", "15"));
        assertFalse(AppUpdateChecker.isNewer("v14", "15"));
    }

    @Test
    public void suffixesDontCount() {
        assertFalse(AppUpdateChecker.isNewer("v15-beta", "15"));
        assertTrue(AppUpdateChecker.isNewer("v16-beta", "15"));
        assertFalse(AppUpdateChecker.isNewer("latest", "15"));
    }
}
