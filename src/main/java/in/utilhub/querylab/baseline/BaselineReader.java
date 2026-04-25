package in.utilhub.querylab.baseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the JSON we ourselves write so we don't take a Jackson/Gson dependency. Schema is fixed
 * (run.json structure) and the parser uses regexes targeted at exactly the fields we need.
 *
 * For richer future schemas, swap to a real JSON parser. v0.2 stays dependency-free.
 */
public final class BaselineReader {

    private static final Pattern FINGERPRINT_HASH = Pattern.compile(
        "\"hash\"\\s*:\\s*\"([0-9a-f]{8,128})\""
    );

    // Inside flags array we have ruleId, fingerprintHash, testMethod — match each block.
    private static final Pattern FLAG_BLOCK = Pattern.compile(
        "\"ruleId\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
            + "\"severity\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*"
            + "\"fingerprintHash\"\\s*:\\s*\"([0-9a-f]+)\"\\s*,\\s*"
            + "\"testMethod\"\\s*:\\s*\"([^\"]+)\"",
        Pattern.DOTALL
    );

    public BaselineSnapshot read(Path baselineFile) throws IOException {
        if (!Files.isRegularFile(baselineFile)) return BaselineSnapshot.EMPTY;
        String json = new String(Files.readAllBytes(baselineFile), StandardCharsets.UTF_8);
        if (json.isEmpty()) return BaselineSnapshot.EMPTY;

        // Restrict fingerprint capture to the "fingerprints" array; the same hash also appears
        // inside flags but we want only the inventory there. Crude but effective: take the
        // substring between "fingerprints":[ and ],"emissionsByTest".
        Set<String> fps = new HashSet<>();
        int fpStart = json.indexOf("\"fingerprints\"");
        if (fpStart >= 0) {
            int arrStart = json.indexOf('[', fpStart);
            int arrEnd = json.indexOf("],\"emissionsByTest\"", arrStart);
            if (arrStart > 0 && arrEnd > arrStart) {
                Matcher m = FINGERPRINT_HASH.matcher(json.substring(arrStart, arrEnd));
                while (m.find()) fps.add(m.group(1));
            }
        }

        Set<String> flags = new HashSet<>();
        int flagsStart = json.indexOf("\"flags\"");
        if (flagsStart >= 0) {
            String slice = json.substring(flagsStart);
            Matcher m = FLAG_BLOCK.matcher(slice);
            while (m.find()) {
                flags.add(BaselineSnapshot.flagKey(m.group(1), m.group(2), m.group(3)));
            }
        }

        return new BaselineSnapshot(fps, flags);
    }
}
