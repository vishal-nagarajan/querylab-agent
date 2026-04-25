package in.utilhub.querylab.explain;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Spring Boot-style {@code application.yml} (and the active-profile variant) for
 * datasource details. v0.2 covers the common case: top-level {@code spring.datasource.{url,username,password}}
 * with {@code ${ENV_VAR}} placeholders resolved against the process environment + system properties.
 *
 * Properties files (.properties) are not handled in v0.2.
 */
public final class AppConfigReader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");

    /** Try YAMLs in order: profile-specific first, then base. Returns null if nothing usable found. */
    public DataSourceConfig read(Path resourcesDir, String activeProfile) {
        if (resourcesDir == null || !Files.isDirectory(resourcesDir)) return null;

        DataSourceConfig fromProfile = activeProfile == null || activeProfile.isEmpty()
            ? null
            : tryRead(resourcesDir.resolve("application-" + activeProfile + ".yml"), "application-" + activeProfile + ".yml");
        if (fromProfile != null && fromProfile.url() != null) return fromProfile;

        return tryRead(resourcesDir.resolve("application.yml"), "application.yml");
    }

    @SuppressWarnings("unchecked")
    private DataSourceConfig tryRead(Path file, String label) {
        if (!Files.isRegularFile(file)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            Object root = new Yaml().load(in);
            if (!(root instanceof Map)) return null;

            Map<String, Object> spring = sub((Map<String, Object>) root, "spring");
            Map<String, Object> ds = sub(spring, "datasource");

            String url      = resolve(asString(ds.get("url")));
            String username = resolve(asString(ds.get("username")));
            String password = resolve(asString(ds.get("password")));

            if (url == null) return null;
            return new DataSourceConfig(url, username, password, label);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Collections.emptyMap();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /** Resolve {@code ${NAME:default}} from env + system properties. */
    static String resolve(String s) {
        if (s == null) return null;
        Matcher m = PLACEHOLDER.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String def = m.group(2);
            String val = System.getProperty(name);
            if (val == null) val = System.getenv(name);
            if (val == null) val = def;
            if (val == null) val = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
