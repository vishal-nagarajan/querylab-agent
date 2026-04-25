package in.utilhub.querylab.scan;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * After the first scan pass, knows which classes are JPA entities and which of their fields are
 * lazy associations. This is what powers Pattern A (lazy-access in loop) detection.
 */
public final class EntityCatalog {

    private final Set<String> entityInternalNames = new HashSet<>();
    /** Entity internal name → field name → true if that field is a LAZY association. */
    private final Map<String, Map<String, Boolean>> lazyFields = new HashMap<>();

    public void ingest(ClassNode cn) {
        if (!hasAnno(cn.visibleAnnotations, ENTITY_ANNOS) && !hasAnno(cn.invisibleAnnotations, ENTITY_ANNOS)) {
            return;
        }
        entityInternalNames.add(cn.name);
        Map<String, Boolean> fields = new HashMap<>();
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                Boolean lazy = lazinessOf(fn);
                if (lazy != null) fields.put(fn.name, lazy);
            }
        }
        lazyFields.put(cn.name, fields);
    }

    public boolean isEntity(String internalName) {
        return entityInternalNames.contains(internalName);
    }

    /** Returns true if the given entity internal name + field is a LAZY-loaded association. */
    public boolean isLazyField(String entityInternal, String fieldName) {
        Map<String, Boolean> m = lazyFields.get(entityInternal);
        return m != null && Boolean.TRUE.equals(m.get(fieldName));
    }

    public int size() {
        return entityInternalNames.size();
    }

    // -------- helpers --------

    /** Looks for a JPA association annotation on the field; returns true if LAZY, false if EAGER, null if neither. */
    private static Boolean lazinessOf(FieldNode fn) {
        Boolean v = inspect(fn.visibleAnnotations);
        if (v != null) return v;
        return inspect(fn.invisibleAnnotations);
    }

    private static Boolean inspect(List<AnnotationNode> anns) {
        if (anns == null) return null;
        for (AnnotationNode an : anns) {
            String desc = an.desc;
            if (desc == null) continue;
            // L<jakarta|javax>/persistence/{OneToMany|ManyToOne|ManyToMany|OneToOne};
            if (desc.endsWith("/persistence/OneToMany;") || desc.endsWith("/persistence/ManyToMany;")) {
                // Default fetch is LAZY for OneToMany/ManyToMany.
                return fetchType(an, true);
            }
            if (desc.endsWith("/persistence/ManyToOne;") || desc.endsWith("/persistence/OneToOne;")) {
                // Default fetch is EAGER for ManyToOne/OneToOne.
                return fetchType(an, false);
            }
        }
        return null;
    }

    private static boolean fetchType(AnnotationNode an, boolean defaultLazy) {
        if (an.values == null) return defaultLazy;
        for (int i = 0; i + 1 < an.values.size(); i += 2) {
            if ("fetch".equals(an.values.get(i))) {
                Object v = an.values.get(i + 1);
                if (v instanceof String[]) {
                    // [enum descriptor, value]
                    String[] arr = (String[]) v;
                    if (arr.length == 2) return "LAZY".equals(arr[1]);
                }
            }
        }
        return defaultLazy;
    }

    private static boolean hasAnno(List<AnnotationNode> anns, Set<String> wanted) {
        if (anns == null) return false;
        for (AnnotationNode an : anns) if (wanted.contains(an.desc)) return true;
        return false;
    }

    private static final Set<String> ENTITY_ANNOS = new HashSet<>();
    static {
        ENTITY_ANNOS.add("Ljakarta/persistence/Entity;");
        ENTITY_ANNOS.add("Ljavax/persistence/Entity;");
    }
}
