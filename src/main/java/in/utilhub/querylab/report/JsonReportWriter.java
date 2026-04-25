package in.utilhub.querylab.report;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Hand-rolled JSON for {@link RunReport}. Avoids pulling Jackson/Gson into the consumer's classpath.
 * Format is stable enough to act as the v0 wire format for the cloud baseline upload.
 */
public final class JsonReportWriter {

    public void write(RunReport report, Path target) throws IOException {
        try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            w.write('{');
            writeKv(w, "capturedAt", report.capturedAt().toString(), true); w.write(',');
            writeKvNum(w, "totalEmissions", report.totalEmissions()); w.write(',');
            writeKvNum(w, "distinctFingerprints", report.distinctFingerprints()); w.write(',');
            writeKvNum(w, "testsExecuted", report.testsExecuted()); w.write(',');

            w.write("\"fingerprints\":[");
            boolean first = true;
            for (Fingerprint f : report.fingerprints()) {
                if (!first) w.write(',');
                first = false;
                w.write('{');
                writeKv(w, "hash", f.hash(), true); w.write(',');
                writeKv(w, "sql", f.sql(), true); w.write(',');
                writeKvNum(w, "totalEmissions", f.totalEmissions()); w.write(',');
                writeKvNum(w, "distinctTests", f.distinctTests());
                w.write('}');
            }
            w.write("],");

            w.write("\"emissionsByTest\":{");
            first = true;
            for (Map.Entry<String, Map<String, Integer>> testEntry : report.emissionsByTest().entrySet()) {
                if (!first) w.write(',');
                first = false;
                w.write('"'); w.write(escape(testEntry.getKey())); w.write("\":{");
                boolean innerFirst = true;
                for (Map.Entry<String, Integer> per : testEntry.getValue().entrySet()) {
                    if (!innerFirst) w.write(',');
                    innerFirst = false;
                    w.write('"'); w.write(escape(per.getKey())); w.write("\":");
                    w.write(Integer.toString(per.getValue()));
                }
                w.write('}');
            }
            w.write("},");

            w.write("\"flags\":[");
            first = true;
            for (Flag fl : report.flags()) {
                if (!first) w.write(',');
                first = false;
                w.write('{');
                writeKv(w, "ruleId", fl.ruleId(), true); w.write(',');
                writeKv(w, "severity", fl.severity().name(), true); w.write(',');
                writeKv(w, "fingerprintHash", fl.fingerprintHash(), true); w.write(',');
                writeKv(w, "testMethod", fl.testMethod(), true); w.write(',');
                writeKv(w, "message", fl.message(), true); w.write(',');
                writeKv(w, "suggestedFix", fl.suggestedFix(), true);
                w.write('}');
            }
            w.write(']');

            w.write('}');
        }
    }

    private static void writeKv(Writer w, String k, String v, boolean quote) throws IOException {
        w.write('"'); w.write(k); w.write("\":");
        if (v == null) {
            w.write("null");
        } else if (quote) {
            w.write('"'); w.write(escape(v)); w.write('"');
        } else {
            w.write(v);
        }
    }

    private static void writeKvNum(Writer w, String k, long v) throws IOException {
        w.write('"'); w.write(k); w.write("\":"); w.write(Long.toString(v));
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
