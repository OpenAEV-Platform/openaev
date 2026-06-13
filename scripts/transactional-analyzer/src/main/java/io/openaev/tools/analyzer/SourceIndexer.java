package io.openaev.tools.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.openaev.tools.analyzer.model.MethodInfo;
import io.openaev.tools.analyzer.model.MethodKey;
import io.openaev.tools.analyzer.model.RepositoryInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * First pass over the source tree: parses every .java file and builds:
 * <ul>
 *   <li>{@link #methodIndex} — all methods discovered, keyed by (class, methodName)</li>
 *   <li>{@link #repositories} — types annotated with @Repository</li>
 *   <li>{@link #fieldTypesByClass} — field-name → declared type FQN per class (used by
 *       CallGraphBuilder to resolve receiver types)</li>
 *   <li>{@link #importsByFile} — import map per file (simple name → FQN)</li>
 * </ul>
 */
public class SourceIndexer {

  // ── Annotation simple-name sets ────────────────────────────────────────────

  private static final Set<String> REPOSITORY_SIMPLE = Set.of("Repository");
  private static final Set<String> SPRING_TX_SIMPLE = Set.of("Transactional");
  private static final Set<String> JAKARTA_TX_FQN =
      Set.of("jakarta.transaction.Transactional", "javax.transaction.Transactional");
  private static final Set<String> HTTP_MAPPING_SIMPLE =
      Set.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
          "RequestMapping");

  // These qualified names identify Spring @Transactional (used to differentiate from Jakarta).
  private static final String SPRING_TX_FQN =
      "org.springframework.transaction.annotation.Transactional";
  private static final String SPRING_REPOSITORY_FQN =
      "org.springframework.stereotype.Repository";

  /** Implicit java.lang types that never appear in import statements. */
  private static final Map<String, String> JAVA_LANG_TYPES = Map.ofEntries(
      Map.entry("AutoCloseable", "java.lang.AutoCloseable"),
      Map.entry("Cloneable", "java.lang.Cloneable"),
      Map.entry("Comparable", "java.lang.Comparable"),
      Map.entry("Iterable", "java.lang.Iterable"),
      Map.entry("Readable", "java.lang.Readable"),
      Map.entry("Runnable", "java.lang.Runnable"),
      Map.entry("Thread", "java.lang.Thread")
  );

  // ── Output indexes ──────────────────────────────────────────────────────────

  /** All indexed methods: MethodKey → MethodInfo. */
  private final Map<MethodKey, MethodInfo> methodIndex = new LinkedHashMap<>();

  /** Repositories in discovery order. */
  private final List<RepositoryInfo> repositories = new ArrayList<>();

  /** Lookup: FQN → RepositoryInfo. */
  private final Map<String, RepositoryInfo> repositoryByFqn = new LinkedHashMap<>();

  /**
   * Field-type information per class.
   * Key: enclosing class FQN → map of (field name → field type FQN).
   */
  final Map<String, Map<String, String>> fieldTypesByClass = new LinkedHashMap<>();

  /**
   * Import map per parsed file.
   * Key: file path string → map of (simple name → FQN).
   */
  final Map<String, Map<String, String>> importsByFile = new LinkedHashMap<>();

  /**
   * Raw (possibly unresolved) supertype simple-names per class, collected during pass 1.
   * Resolved to FQNs during pass 2 ({@link #resolveOverrides()}).
   * Key: class FQN → list of supertype simple names (extends + implements).
   */
  private final Map<String, List<String>> rawSupertypesByClass = new LinkedHashMap<>();

  /** All parsed compilation units, in order. */
  final List<ParsedFile> parsedFiles = new ArrayList<>();

  private Path repoRoot;

  // ── Public API ──────────────────────────────────────────────────────────────

  public void setRepoRoot(Path repoRoot) {
    this.repoRoot = repoRoot;
  }

  /** Parse every .java file under {@code sourceRoot} and populate the indexes. */
  public void indexSourceRoot(Path sourceRoot) throws IOException {
    List<Path> javaFiles;
    try (Stream<Path> stream = Files.walk(sourceRoot)) {
      javaFiles = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
    }
    System.out.printf("  Parsing %d files from %s%n", javaFiles.size(), sourceRoot);

    for (Path file : javaFiles) {
      try {
        CompilationUnit cu = StaticJavaParser.parse(file);
        ParsedFile pf = new ParsedFile(file, cu);
        parsedFiles.add(pf);
        indexFile(pf);
      } catch (Exception e) {
        System.err.printf("  WARN: skipping %s — %s%n", file.getFileName(), e.getMessage());
      }
    }
    resolveOverrides();
  }

  // ── Private: indexing logic ─────────────────────────────────────────────────

  private void indexFile(ParsedFile pf) {
    CompilationUnit cu = pf.cu;
    String packageName = cu.getPackageDeclaration()
        .map(pd -> pd.getNameAsString())
        .orElse("");

    // Build import map for this file
    Map<String, String> imports = buildImports(cu);
    importsByFile.put(pf.filePath.toString(), imports);

    // Process each type declaration (top-level and nested)
    for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
      indexType(type, packageName, pf, imports);
    }
  }

  private void indexType(TypeDeclaration<?> type, String packageName, ParsedFile pf,
      Map<String, String> imports) {
    String simpleName = type.getNameAsString();
    String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    String relPath = relativize(pf.filePath);

    boolean classIsRepository = hasAnnotation(type.getAnnotations(), REPOSITORY_SIMPLE,
        SPRING_REPOSITORY_FQN, imports);
    boolean classIsSpringTx = hasAnnotation(type.getAnnotations(), SPRING_TX_SIMPLE,
        SPRING_TX_FQN, imports);
    boolean classIsJakartaTx = hasAnnotationFqn(type.getAnnotations(), JAKARTA_TX_FQN, imports);

    // ── Index fields (for call graph receiver resolution) ──────────────────
    Map<String, String> fieldTypes = new LinkedHashMap<>();
    for (FieldDeclaration field : type.getFields()) {
      String rawType = stripGenerics(field.getElementType().asString());
      String resolvedType = resolveTypeName(rawType, imports);
      for (VariableDeclarator var : field.getVariables()) {
        fieldTypes.put(var.getNameAsString(), resolvedType);
      }
    }
    if (!fieldTypes.isEmpty()) {
      fieldTypesByClass.put(qualifiedName, fieldTypes);
    }

    // ── Index methods ───────────────────────────────────────────────────────
    List<MethodInfo> declaredMethods = new ArrayList<>();

    for (MethodDeclaration method : type.getMethods()) {
      String methodName = method.getNameAsString();
      int arity = method.getParameters().size();
      MethodKey key = new MethodKey(qualifiedName, methodName, arity);

      List<AnnotationExpr> annotations = method.getAnnotations();
      boolean methodSpringTx = classIsSpringTx
          || hasAnnotation(annotations, SPRING_TX_SIMPLE, SPRING_TX_FQN, imports);
      boolean methodJakartaTx = classIsJakartaTx
          || hasAnnotationFqn(annotations, JAKARTA_TX_FQN, imports);
      boolean isHttp = hasAnnotation(annotations, HTTP_MAPPING_SIMPLE, null, imports);
      boolean hasOverride = annotations.stream()
          .anyMatch(a -> "Override".equals(a.getNameAsString()));

      String params = "(" + method.getParameters().stream()
          .map(p -> stripGenerics(p.getType().asString()))
          .collect(Collectors.joining(", ")) + ")";
      int line = method.getBegin().map(pos -> pos.line).orElse(-1);

      MethodInfo info = new MethodInfo(key, methodSpringTx, methodJakartaTx,
          classIsRepository, isHttp, relPath, line, params);
      info.hasOverride = hasOverride;

      methodIndex.put(key, info);
      declaredMethods.add(info);
    }

    // ── Collect supertype names for override resolution (pass 2) ──────────
    if (type instanceof ClassOrInterfaceDeclaration) {
      ClassOrInterfaceDeclaration coid = (ClassOrInterfaceDeclaration) type;
      List<String> supertypeNames = new ArrayList<>();
      coid.getExtendedTypes().forEach(t ->
          supertypeNames.add(resolveSupertypeName(stripGenerics(t.getNameAsString()), imports)));
      coid.getImplementedTypes().forEach(t ->
          supertypeNames.add(resolveSupertypeName(stripGenerics(t.getNameAsString()), imports)));
      if (!supertypeNames.isEmpty()) {
        rawSupertypesByClass.put(qualifiedName, supertypeNames);
      }
    }

    if (classIsRepository) {
      RepositoryInfo repoInfo = new RepositoryInfo(qualifiedName, simpleName, relPath,
          declaredMethods);
      repositories.add(repoInfo);
      repositoryByFqn.put(qualifiedName, repoInfo);
    }
  }

  // ── Pass 2: resolve overridesFrom ──────────────────────────────────────────

  /**
   * For every indexed method whose declaring class has supertypes, checks whether a method with
   * the same name exists in any of those supertypes (in the indexed source). If found, sets
   * {@link MethodInfo#overridesFrom} to the simple name of the first matching supertype.
   * Skips @Repository methods (they are interface methods by design and their origin is obvious).
   */
  private void resolveOverrides() {
    Set<String> indexedClasses = new HashSet<>();
    for (MethodKey key : methodIndex.keySet()) {
      indexedClasses.add(key.qualifiedClassName());
    }

    for (Map.Entry<String, List<String>> entry : rawSupertypesByClass.entrySet()) {
      String classFqn = entry.getKey();
      List<String> supertypeNames = entry.getValue();

      // Partition supertypes into indexed (present in our codebase) and external (library types)
      List<String> indexedFqns = new ArrayList<>();
      List<String> externalFqns = new ArrayList<>();
      for (String name : supertypeNames) {
        String fqn = resolveToIndexedFqn(name, indexedClasses);
        if (fqn != null) indexedFqns.add(fqn);
        else externalFqns.add(name); // already FQN or best-effort simple name
      }

      for (Map.Entry<MethodKey, MethodInfo> mEntry : methodIndex.entrySet()) {
        MethodKey mKey = mEntry.getKey();
        if (!mKey.qualifiedClassName().equals(classFqn)) continue;
        MethodInfo mi = mEntry.getValue();

        // 1. Indexed supertypes: match by method name (most reliable)
        String found = null;
        for (String fqn : indexedFqns) {
          if (methodIndex.containsKey(new MethodKey(fqn, mKey.methodName(), mKey.arity()))) {
            found = fqn; // store full FQN for sorting/filtering
            break;
          }
        }

        // 2. External supertypes: if method has @Override and no indexed match,
        //    use external supertypes as best-effort (compiler guarantees one of them declares it)
        if (found == null && mi.hasOverride && !externalFqns.isEmpty()) {
          found = externalFqns.size() == 1
              ? externalFqns.get(0)
              : String.join(",", externalFqns); // store all for multi-external case
        }

        mi.overridesFrom = found;
      }
    }
  }

  /**
   * Finds a fully-qualified class name in the indexed classes set whose simple name matches.
   * Returns the first (and ideally unique) match, or null.
   */
  private String resolveToIndexedFqn(String simpleName, Set<String> indexedClasses) {
    if (indexedClasses.contains(simpleName)) return simpleName; // already FQN
    String found = null;
    for (String fqn : indexedClasses) {
      String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
      if (simple.equals(simpleName)) {
        if (found == null) found = fqn;
        else return null; // ambiguous
      }
    }
    return found;
  }

  // ── Annotation helpers ──────────────────────────────────────────────────────

  /**
   * Returns true when any annotation in the list matches either a simple name in {@code
   * simpleNames} (after resolving via imports to confirm it maps to {@code expectedFqn} when
   * provided) or a fully-qualified name equal to {@code expectedFqn}.
   */
  private boolean hasAnnotation(List<AnnotationExpr> annotations,
      Set<String> simpleNames, String expectedFqn, Map<String, String> imports) {
    for (AnnotationExpr ann : annotations) {
      String name = ann.getNameAsString();
      // Direct simple-name match
      if (simpleNames.contains(name)) {
        if (expectedFqn == null) return true;
        // Verify via import: if imported as a different FQN, skip
        String imported = imports.get(name);
        if (imported == null || imported.equals(expectedFqn)) return true;
      }
      // Full FQN match
      if (expectedFqn != null && expectedFqn.equals(name)) return true;
    }
    return false;
  }

  /** Returns true when any annotation matches one of the given FQNs (checked by import or full name). */
  private boolean hasAnnotationFqn(List<AnnotationExpr> annotations,
      Set<String> fqns, Map<String, String> imports) {
    for (AnnotationExpr ann : annotations) {
      String name = ann.getNameAsString();
      if (fqns.contains(name)) return true;
      String resolved = imports.getOrDefault(name, name);
      if (fqns.contains(resolved)) return true;
    }
    return false;
  }

  // ── Type resolution helpers ─────────────────────────────────────────────────

  /** Strips generic type parameters: {@code List<String>} → {@code List}. */
  static String stripGenerics(String typeName) {
    int lt = typeName.indexOf('<');
    return lt >= 0 ? typeName.substring(0, lt).trim() : typeName.trim();
  }

  /**
   * Resolves a simple type name to a fully-qualified name using the import map, falling back to
   * searching the method index for a unique match.
   */
  /**
   * Resolves a supertype simple name to the best available FQN, in priority order:
   * 1. Already qualified (contains '.') — returned as-is
   * 2. Found in file's explicit imports
   * 3. Found in indexed classes (unique match)
   * 4. Known java.lang type
   * 5. Falls back to simple name
   */
  String resolveSupertypeName(String simpleName, Map<String, String> imports) {
    if (simpleName.contains(".")) return simpleName;
    String fromImport = imports.get(simpleName);
    if (fromImport != null) return fromImport;
    // Try indexed classes for a unique match
    String fromIndex = null;
    for (String fqn : methodIndex.keySet().stream()
        .map(MethodKey::qualifiedClassName)
        .collect(Collectors.toSet())) {
      if (fqn.endsWith("." + simpleName) || fqn.equals(simpleName)) {
        if (fromIndex == null) fromIndex = fqn;
        else { fromIndex = null; break; } // ambiguous
      }
    }
    if (fromIndex != null) return fromIndex;
    // java.lang implicit imports
    String javaLang = JAVA_LANG_TYPES.get(simpleName);
    if (javaLang != null) return javaLang;
    return simpleName;
  }

  String resolveTypeName(String simpleName, Map<String, String> imports) {
    if (simpleName.contains(".")) return simpleName; // already qualified
    String fromImport = imports.get(simpleName);
    if (fromImport != null) return fromImport;
    // Search indexed classes for a unique match by simple name
    String found = null;
    for (String fqn : methodIndex.keySet().stream()
        .map(MethodKey::qualifiedClassName)
        .collect(Collectors.toSet())) {
      if (fqn.endsWith("." + simpleName) || fqn.equals(simpleName)) {
        if (found == null) found = fqn;
        else return simpleName; // ambiguous — keep as-is
      }
    }
    return found != null ? found : simpleName;
  }

  /** Builds the simple-name → FQN import map for a compilation unit. */
  private Map<String, String> buildImports(CompilationUnit cu) {
    Map<String, String> imports = new LinkedHashMap<>();
    cu.getImports().forEach(imp -> {
      if (!imp.isAsterisk() && !imp.isStatic()) {
        String fqn = imp.getNameAsString();
        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
        imports.put(simple, fqn);
      }
    });
    return imports;
  }

  private String relativize(Path file) {
    if (repoRoot != null && file.startsWith(repoRoot)) {
      return repoRoot.relativize(file).toString();
    }
    return file.toString();
  }

  // ── Accessors ───────────────────────────────────────────────────────────────

  public Map<MethodKey, MethodInfo> getMethodIndex() { return methodIndex; }
  public List<RepositoryInfo> getRepositories() { return repositories; }
  public Map<String, RepositoryInfo> getRepositoryByFqn() { return repositoryByFqn; }
  public List<ParsedFile> getParsedFiles() { return parsedFiles; }

  public int getMethodCount() { return methodIndex.size(); }
  public int getClassCount() {
    return parsedFiles.stream()
        .mapToInt(pf -> pf.cu.findAll(TypeDeclaration.class).size())
        .sum();
  }
  public int getRepositoryCount() { return repositories.size(); }

  public MethodInfo getOrCreate(MethodKey key) {
    return methodIndex.getOrDefault(key, MethodInfo.unknown(key));
  }

  public boolean isRepositoryType(String qualifiedName) {
    return repositoryByFqn.containsKey(qualifiedName);
  }

  // ── Inner class: parsed file container ─────────────────────────────────────

  public static class ParsedFile {
    public final Path filePath;
    public final CompilationUnit cu;

    public ParsedFile(Path filePath, CompilationUnit cu) {
      this.filePath = filePath;
      this.cu = cu;
    }
  }
}
