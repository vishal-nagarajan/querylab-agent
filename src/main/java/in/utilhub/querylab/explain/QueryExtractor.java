package in.utilhub.querylab.explain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Walks compiled .class files looking for {@code @org.springframework.data.jpa.repository.Query}
 * annotations and harvests the SQL/JPQL strings + their {@code nativeQuery} flag.
 *
 * v0.2 limitation: only native queries ({@code nativeQuery = true}) are surfaced for EXPLAIN —
 * JPQL ones come back tagged as such but are skipped by the runner because we don't want to
 * pull in Hibernate's full SQM translator at plugin time. JPQL handling lands in v0.3.
 */
public final class QueryExtractor {

    private static final String QUERY_DESC = "Lorg/springframework/data/jpa/repository/Query;";

    private final Consumer<String> debug;

    public QueryExtractor(Consumer<String> debug) {
        this.debug = debug;
    }

    public List<ExtractedQuery> extract(Path classesRoot) throws IOException {
        List<ExtractedQuery> out = new ArrayList<>();
        if (!Files.isDirectory(classesRoot)) return out;

        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(p -> p.toString().endsWith(".class") && Files.isRegularFile(p))
                .forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        ClassNode cn = new ClassNode();
                        new ClassReader(in).accept(cn, ClassReader.SKIP_FRAMES);
                        scanClass(cn, out);
                    } catch (IOException e) {
                        debug.accept("could not read " + p + ": " + e.getMessage());
                    }
                });
        }
        return out;
    }

    private void scanClass(ClassNode cn, List<ExtractedQuery> out) {
        if (cn.methods == null) return;
        String dotted = cn.name.replace('/', '.');
        for (MethodNode m : cn.methods) {
            String[] q = readQueryAnnotation(m);
            if (q == null) continue;
            String sql = q[0];
            boolean isNative = "true".equals(q[1]);
            out.add(new ExtractedQuery(dotted, m.name, sql, isNative));
        }
    }

    /** @return {@code [sql, nativeQuery as String]} if {@code @Query} is present, else {@code null}. */
    private static String[] readQueryAnnotation(MethodNode m) {
        AnnotationNode an = find(m.visibleAnnotations);
        if (an == null) an = find(m.invisibleAnnotations);
        if (an == null) return null;

        String sql = null;
        String isNative = "false";
        if (an.values != null) {
            for (int i = 0; i + 1 < an.values.size(); i += 2) {
                Object name = an.values.get(i);
                Object val = an.values.get(i + 1);
                if ("value".equals(name) && val instanceof String) {
                    sql = (String) val;
                } else if ("nativeQuery".equals(name) && val instanceof Boolean) {
                    isNative = String.valueOf((Boolean) val);
                }
            }
        }
        if (sql == null) return null;
        return new String[]{ sql, isNative };
    }

    private static AnnotationNode find(List<AnnotationNode> anns) {
        if (anns == null) return null;
        for (AnnotationNode an : anns) if (QUERY_DESC.equals(an.desc)) return an;
        return null;
    }
}
