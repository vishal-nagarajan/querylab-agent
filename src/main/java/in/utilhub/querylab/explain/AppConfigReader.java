package in.utilhub.querylab.explain;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Spring Boot-style {@code application.{yml,yaml,properties}} (and the active-profile
 * variants) for datasource details. We deliberately try a wide net so users don't have to
 * configure querylab — we look in:
 *
 * <ol>
 *   <li>{@code src/main/resources/application-{profile}.{yml,yaml,properties}}</li>
 *   <li>{@code src/main/resources/application.{yml,yaml,properties}}</li>
 *   <li>{@code src/test/resources/application-{profile}.{yml,yaml,properties}}</li>
 *   <li>{@code src/test/resources/application.{yml,yaml,properties}}</li>
 * </ol>
 *
 * Placeholders {@code ${ENV_VAR:default}} are resolved against system properties first, then env vars.
 *
 * Returns the first datasource it finds. If you want to force one, pass {@code -Dquerylab.explain.url=...}
 * to the Mojo (Mojo params take precedence over this reader).
 */
public final class AppConfigReader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");

    /**
     * Result of a search attempt — either we found a config, or we have a list of paths we tried
     * so the caller can produce a useful diagnostic.
     */
    public static final class Result {
        public final DataSourceConfig config;        // null if not found
        public final List<String> searchedPaths;
        Result(DataSourceConfig config, List<String> searchedPaths) {
            this.config = config;
            this.searchedPaths = searchedPaths;
        }
    }

    public Result read(Path projectBase, String activeProfile) {
        List<String> searched = new ArrayList<>();
        if (projectBase == null) return new Result(null, searched);

        List<Path> dirs = new ArrayList<>();
        Path mainRes = projectBase.resolve("src").resolve("main").resolve("resources");
        Path testRes = projectBase.resolve("src").resolve("test").resolve("resources");
        if (Files.isDirectory(mainRes)) dirs.add(mainRes);
        if (Files.isDirectory(testRes)) dirs.add(testRes);

        String[] profileSuffixes = activeProfile != null && !activeProfile.isEmpty()
            ? new String[]{"-" + activeProfile, ""}
            : new String[]{""};
        String[] extensions = {"yml", "yaml", "properties"};

        for (Path dir : dirs) {
            for (String suffix : profileSuffixes) {
                for (String ext : extensions) {
                    Path file = dir.resolve("application" + suffix + "." + ext);
                    searched.add(file.toString());
                    if (!Files.isRegularFile(file)) continue;

                    DataSourceConfig cfg = ext.equals("properties")
                        ? readProperties(file)
                        : readYaml(file);
                    if (cfg != null && cfg.url() != null) {
                        return new Result(cfg, searched);
                    }
                }
            }
        }
        return new Result(null, searched);
    }

    @SuppressWarnings("unchecked")
    private DataSourceConfig readYaml(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Object root = new Yaml().load(in);
            if (!(root instanceof Map)) return null;

            Map<String, Object> spring = sub((Map<String, Object>) root, "spring");
            Map<String, Object> ds = sub(spring, "datasource");

            String url = resolve(asString(ds.get("url")));
            if (url == null) return null;
            String user = resolve(asString(ds.get("username")));
            String pass = resolve(asString(ds.get("password")));
            return new DataSourceConfig(url, user, pass, file.getFileName().toString());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private DataSourceConfig readProperties(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String url = resolve(props.getProperty("spring.datasource.url"));
            if (url == null) return null;
            String user = resolve(props.getProperty("spring.datasource.username"));
            String pass = resolve(props.getProperty("spring.datasource.password"));
            return new DataSourceConfig(url, user, pass, file.getFileName().toString());
        } catch (IOException e) {
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

    /** Resolve {@code ${NAME:default}} from system properties first, then env vars. */
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
