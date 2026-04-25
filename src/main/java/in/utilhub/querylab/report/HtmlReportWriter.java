package in.utilhub.querylab.report;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-file HTML report. Mirrors the dark/amber visual language of the landing page so the OSS
 * artifact and the cloud product feel like one product.
 *
 * Pure server-side render in Java — no client JS required. Self-contained: no external CSS, no
 * external fonts (system monospace stack), opens fine offline.
 */
public final class HtmlReportWriter {

    public void write(RunReport report, Path target) throws IOException {
        try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            w.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">");
            w.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
            w.append("<title>querylab — run report</title>");
            w.append("<style>");
            w.append(STYLE);
            w.append("</style></head><body>");

            // Header
            w.append("<header class=\"top\"><span class=\"mark\">[ ]</span>");
            w.append("<span class=\"name\">querylab<span class=\"host\"> · run report</span></span>");
            w.append("<span class=\"meta\">").append(esc(report.capturedAt().toString())).append("</span>");
            w.append("</header>");

            // Stats row
            w.append("<section class=\"stats\">");
            statTile(w, "tests",         Long.toString(report.testsExecuted()), null);
            statTile(w, "distinct",      Long.toString(report.distinctFingerprints()), null);
            statTile(w, "emissions",     Long.toString(report.totalEmissions()), null);
            statTile(w, "flags",         Long.toString(report.flags().size()),
                     report.flags().isEmpty() ? null : "bad");
            w.append("</section>");

            // Flags section
            w.append("<section class=\"sec\"><div class=\"sec-h\">// flags</div>");
            if (report.flags().isEmpty()) {
                w.append("<div class=\"empty\">no flags raised — clean run.</div>");
            } else {
                Map<String, String> sqlByHash = sqlByHash(report.fingerprints());
                for (Flag fl : report.flags()) {
                    w.append("<div class=\"flag ").append(severityClass(fl.severity())).append("\">");
                    w.append("<div class=\"flag-h\"><span class=\"chip\">").append(esc(fl.ruleId())).append("</span>");
                    w.append("<span class=\"sev\">").append(esc(fl.severity().name())).append("</span>");
                    w.append("<span class=\"test\">").append(esc(fl.testMethod())).append("</span></div>");
                    w.append("<div class=\"flag-msg\">").append(esc(fl.message())).append("</div>");
                    String sql = sqlByHash.getOrDefault(fl.fingerprintHash(), "");
                    if (!sql.isEmpty()) {
                        w.append("<pre class=\"sql\">").append(esc(sql)).append("</pre>");
                    }
                    if (fl.suggestedFix() != null && !fl.suggestedFix().isEmpty()) {
                        w.append("<div class=\"fix\">→ ").append(esc(fl.suggestedFix())).append("</div>");
                    }
                    w.append("</div>");
                }
            }
            w.append("</section>");

            // Inventory section
            w.append("<section class=\"sec\"><div class=\"sec-h\">// inventory</div>");
            w.append("<div class=\"inv-head\"><span>fingerprint</span><span>tests</span><span>emissions</span><span>sql</span></div>");
            for (Fingerprint f : report.fingerprints()) {
                String state = (f.totalEmissions() > 5) ? "warn" : "ok";
                w.append("<div class=\"inv ").append(state).append("\">");
                w.append("<span class=\"hash\">").append(esc(shortHash(f.hash()))).append("</span>");
                w.append("<span class=\"num\">").append(Integer.toString(f.distinctTests())).append("</span>");
                w.append("<span class=\"num strong\">").append(Integer.toString(f.totalEmissions())).append("</span>");
                w.append("<span class=\"sql-cell\">").append(esc(truncate(f.sql(), 200))).append("</span>");
                w.append("</div>");
            }
            w.append("</section>");

            // By test section
            w.append("<section class=\"sec\"><div class=\"sec-h\">// by test</div>");
            for (Map.Entry<String, Map<String, Integer>> testEntry : report.emissionsByTest().entrySet()) {
                int total = testEntry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                w.append("<div class=\"test-row\">");
                w.append("<div class=\"test-head\"><span class=\"test-name\">")
                    .append(esc(testEntry.getKey())).append("</span>");
                w.append("<span class=\"meta\">").append(Integer.toString(testEntry.getValue().size()))
                    .append(" fingerprints · ").append(Integer.toString(total)).append(" emissions</span></div>");
                for (Map.Entry<String, Integer> per : testEntry.getValue().entrySet()) {
                    String state = per.getValue() > 5 ? "warn" : "ok";
                    w.append("<div class=\"test-fp ").append(state).append("\">");
                    w.append("<span class=\"hash\">").append(esc(shortHash(per.getKey()))).append("</span>");
                    w.append("<span class=\"num strong\">× ").append(Integer.toString(per.getValue())).append("</span>");
                    w.append("</div>");
                }
                w.append("</div>");
            }
            w.append("</section>");

            w.append("<footer class=\"foot\">querylab v0.1 · jpa.utilhub.in · Apache-2.0</footer>");
            w.append("</body></html>");
        }
    }

    private void statTile(Writer w, String label, String value, String tone) throws IOException {
        w.append("<div class=\"stat");
        if (tone != null) { w.append(' '); w.append(tone); }
        w.append("\"><div class=\"k\">").append(esc(label)).append("</div>");
        w.append("<div class=\"v\">").append(esc(value)).append("</div></div>");
    }

    private static Map<String, String> sqlByHash(List<Fingerprint> fps) {
        Map<String, String> m = new HashMap<>();
        for (Fingerprint f : fps) m.put(f.hash(), f.sql());
        return m;
    }

    private static String shortHash(String h) {
        return h == null ? "" : (h.length() > 12 ? h.substring(0, 12) : h);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + " …";
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String severityClass(Flag.Severity s) {
        switch (s) {
            case BAD:  return "bad";
            case WARN: return "warn";
            default:   return "info";
        }
    }

    // -------- styles inlined ----------------------------------------

    private static final String STYLE =
        ":root{" +
        "--bg:#1a1612;--bg1:#211c17;--bg2:#28221c;--line:#3a3027;--soft:#2c2520;" +
        "--fg:#ece1d3;--dim:#bfb09a;--mute:#8a7e6e;--faint:#5a5044;" +
        "--amber:#e8a04a;--amber2:#d68930;--red:#e07560;--grn:#88c272;" +
        "}" +
        "*{box-sizing:border-box}" +
        "html,body{margin:0;padding:0;background:var(--bg);color:var(--fg);" +
        "font-family:ui-monospace,'JetBrains Mono','SF Mono',Menlo,Consolas,monospace;font-size:14px;line-height:1.55}" +
        ".top{display:flex;align-items:center;gap:12px;padding:14px 28px;border-bottom:1px solid var(--soft);background:var(--bg1)}" +
        ".mark{display:inline-grid;place-items:center;width:24px;height:24px;border:1px solid var(--line);border-radius:4px;color:var(--amber);font-size:12px}" +
        ".name{font-weight:600}.host{color:var(--mute);font-weight:400}" +
        ".top .meta{margin-left:auto;color:var(--faint);font-size:12px}" +
        ".stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:24px 28px;max-width:1200px;margin:0 auto;width:100%}" +
        ".stat{padding:16px;border:1px solid var(--soft);border-radius:8px;background:var(--bg1)}" +
        ".stat .k{color:var(--mute);font-size:11px;letter-spacing:.08em;text-transform:uppercase}" +
        ".stat .v{color:var(--fg);font-size:28px;margin-top:6px;font-variant-numeric:tabular-nums}" +
        ".stat.bad .v{color:var(--red)}" +
        ".sec{padding:24px 28px;max-width:1200px;margin:0 auto;width:100%}" +
        ".sec-h{color:var(--mute);font-size:12px;letter-spacing:.1em;margin-bottom:12px}" +
        ".empty{color:var(--mute);padding:14px;border:1px dashed var(--soft);border-radius:6px}" +
        ".flag{padding:14px 16px;border:1px solid var(--soft);border-radius:8px;margin-bottom:10px;background:var(--bg1);border-left:3px solid var(--mute)}" +
        ".flag.bad{border-left-color:var(--red)}.flag.warn{border-left-color:var(--amber)}" +
        ".flag-h{display:flex;align-items:center;gap:10px;font-size:12px;color:var(--dim)}" +
        ".chip{padding:2px 8px;border:1px solid var(--line);border-radius:99px;color:var(--amber);font-size:11px}" +
        ".sev{color:var(--red);letter-spacing:.06em;font-size:11px}" +
        ".flag.warn .sev{color:var(--amber)}" +
        ".flag .test{margin-left:auto;color:var(--faint);font-size:11px}" +
        ".flag-msg{color:var(--fg);margin-top:8px}" +
        ".sql{margin:10px 0 0;padding:10px 12px;background:#13100c;border:1px solid var(--soft);border-radius:6px;color:var(--dim);overflow-x:auto;white-space:pre-wrap;font-size:12.5px}" +
        ".fix{margin-top:8px;color:var(--grn);font-size:13px}" +
        ".inv-head,.inv{display:grid;grid-template-columns:120px 80px 110px 1fr;gap:12px;padding:8px 12px;font-size:12.5px;align-items:center}" +
        ".inv-head{color:var(--mute);text-transform:uppercase;letter-spacing:.06em;font-size:11px;border-bottom:1px solid var(--soft)}" +
        ".inv{border:1px solid var(--soft);border-radius:6px;background:var(--bg1);margin-top:6px}" +
        ".inv.warn{border-color:#5b3a1f}" +
        ".hash{font-family:inherit;color:var(--faint)}" +
        ".num{text-align:right;color:var(--dim);font-variant-numeric:tabular-nums}" +
        ".num.strong{color:var(--fg);font-weight:600}" +
        ".sql-cell{color:var(--dim);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        ".test-row{margin-bottom:14px}" +
        ".test-head{display:flex;gap:10px;align-items:baseline;padding:6px 4px;border-bottom:1px dashed var(--soft);margin-bottom:6px}" +
        ".test-name{color:var(--fg)}.test-head .meta{margin-left:auto;color:var(--faint);font-size:11px}" +
        ".test-fp{display:grid;grid-template-columns:140px 80px 1fr;gap:12px;padding:5px 8px;font-size:12.5px;color:var(--dim)}" +
        ".test-fp.warn .num.strong{color:var(--red)}" +
        ".foot{padding:24px 28px;color:var(--faint);font-size:11px;border-top:1px solid var(--soft);text-align:center;margin-top:24px}";
}
