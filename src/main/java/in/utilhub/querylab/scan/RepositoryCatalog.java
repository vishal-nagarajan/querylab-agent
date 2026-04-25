package in.utilhub.querylab.scan;

import org.objectweb.asm.tree.ClassNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which interfaces transitively extend a Spring Data repository marker. Used to recognise
 * INVOKEINTERFACE calls inside loops as candidate Pattern B (repo-call-in-loop) N+1 sites.
 */
public final class RepositoryCatalog {

    /**
     * Spring Data marker interfaces. Anything extending these (transitively) is a repository.
     */
    private static final Set<String> ROOT_REPOSITORY_NAMES = new HashSet<>(Arrays.asList(
        "org/springframework/data/repository/Repository",
        "org/springframework/data/repository/CrudRepository",
        "org/springframework/data/repository/PagingAndSortingRepository",
        "org/springframework/data/repository/ListCrudRepository",
        "org/springframework/data/repository/ListPagingAndSortingRepository",
        "org/springframework/data/jpa/repository/JpaRepository"
    ));

    /** Class internal name → its declared superclass + interface internal names (raw, not resolved). */
    private final Map<String, String[]> superinterfaces = new HashMap<>();

    /** Memoized: is this internal name (transitively) a repository? */
    private final Map<String, Boolean> isRepoCache = new HashMap<>();

    public void ingest(ClassNode cn) {
        // Track the parents so we can resolve transitively after all classes are ingested.
        List<String> parents = cn.interfaces != null ? cn.interfaces : java.util.Collections.emptyList();
        String[] arr = new String[parents.size() + (cn.superName == null ? 0 : 1)];
        int i = 0;
        if (cn.superName != null) arr[i++] = cn.superName;
        for (String p : parents) arr[i++] = p;
        superinterfaces.put(cn.name, arr);
    }

    public boolean isRepositoryInterface(String internalName) {
        return isRepoCache.computeIfAbsent(internalName, this::computeIsRepo);
    }

    private boolean computeIsRepo(String name) {
        if (name == null) return false;
        if (ROOT_REPOSITORY_NAMES.contains(name)) return true;
        String[] parents = superinterfaces.get(name);
        if (parents == null) return false;
        for (String p : parents) {
            if (computeIsRepo(p)) return true;
        }
        return false;
    }

    public int size() {
        // count interfaces we believe are repositories
        int n = 0;
        for (String name : superinterfaces.keySet()) if (isRepositoryInterface(name)) n++;
        return n;
    }
}
