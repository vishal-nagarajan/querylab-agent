package in.utilhub.querylab.baseline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader uses targeted regex parsing (no JSON dependency) — easy to break silently if our
 * own JsonReportWriter format ever drifts. These tests pin the contract.
 */
class BaselineReaderTest {

    @Test
    void emptyFileReturnsEmptySnapshot(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve("baseline.json");
        Files.writeString(baseline, "");
        BaselineSnapshot s = new BaselineReader().read(baseline);
        assertTrue(s.isEmpty());
    }

    @Test
    void missingFileReturnsEmpty(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve("missing.json");
        BaselineSnapshot s = new BaselineReader().read(baseline);
        assertTrue(s.isEmpty());
    }

    @Test
    void readsFingerprintHashesAndFlags(@TempDir Path tmp) throws Exception {
        String json =
            "{\"capturedAt\":\"2026-01-01\",\"totalEmissions\":3,\"distinctFingerprints\":2,"
            + "\"testsExecuted\":1,"
            + "\"fingerprints\":["
            +   "{\"hash\":\"aaaaaaaaaaaaaaaa\",\"sql\":\"select 1\",\"totalEmissions\":1,\"distinctTests\":1},"
            +   "{\"hash\":\"bbbbbbbbbbbbbbbb\",\"sql\":\"select 2\",\"totalEmissions\":2,\"distinctTests\":1}"
            + "],"
            + "\"emissionsByTest\":{\"x\":{\"aaaaaaaaaaaaaaaa\":1}},"
            + "\"flags\":["
            +   "{\"ruleId\":\"n_plus_one\",\"severity\":\"BAD\","
            +    "\"fingerprintHash\":\"bbbbbbbbbbbbbbbb\",\"testMethod\":\"FooTest#bar\","
            +    "\"message\":\"x\",\"suggestedFix\":\"y\"}"
            + "]}";

        Path baseline = tmp.resolve("baseline.json");
        Files.writeString(baseline, json);
        BaselineSnapshot s = new BaselineReader().read(baseline);

        assertEquals(2, s.knownFingerprintCount());
        assertTrue(s.knowsFingerprint("aaaaaaaaaaaaaaaa"));
        assertTrue(s.knowsFingerprint("bbbbbbbbbbbbbbbb"));
        assertFalse(s.knowsFingerprint("cccccccccccccccc"));

        assertEquals(1, s.knownFlagCount());
        assertTrue(s.knowsFlag("n_plus_one", "bbbbbbbbbbbbbbbb", "FooTest#bar"));
        assertFalse(s.knowsFlag("n_plus_one", "bbbbbbbbbbbbbbbb", "OtherTest#bar"));
    }
}
