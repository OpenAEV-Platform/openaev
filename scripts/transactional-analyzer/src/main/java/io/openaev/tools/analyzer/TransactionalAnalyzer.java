package io.openaev.tools.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.openaev.tools.analyzer.model.CallerNode;
import io.openaev.tools.analyzer.model.MethodInfo;
import io.openaev.tools.analyzer.model.MethodKey;
import io.openaev.tools.analyzer.model.RepositoryInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Entry point for the transactional caller-tree analyzer.
 *
 * <p>Usage:
 * <pre>
 *   # Build fat JAR
 *   mvn package
 *
 *   # Run analysis
 *   java -jar target/transactional-analyzer-1.0.0.jar /path/to/repo output.html
 *
 *   # Or via Maven without building the JAR first
 *   mvn exec:java -Dexec.args="/path/to/repo output.html"
 * </pre>
 *
 * <p>The tool:
 * <ol>
 *   <li>Locates Maven source roots ({@code src/main/java}) under the repo.</li>
 *   <li>Configures JavaParser with its symbol solver pointing at those source roots.</li>
 *   <li>Indexes every {@code .java} file: records methods, annotations, field types.</li>
 *   <li>Builds a reverse call graph (callee → set of callers).</li>
 *   <li>Constructs upward caller trees for every method declared in a {@code @Repository} type.</li>
 *   <li>Emits a self-contained HTML report.</li>
 * </ol>
 */
public class TransactionalAnalyzer {

  /** Maximum caller-tree depth. Prevents combinatorial explosion on large codebases. */
  private static final int MAX_DEPTH = 25;

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: TransactionalAnalyzer <repo-root> <output.html>");
      System.err.println();
      System.err.println("Example:");
      System.err.println("  java -jar transactional-analyzer.jar /path/to/openaev report.html");
      System.exit(1);
    }

    Path repoRoot = Path.of(args[0]).toAbsolutePath().normalize();
    Path outputFile = Path.of(args[1]);

    if (!Files.isDirectory(repoRoot)) {
      System.err.println("ERROR: repo-root is not a directory: " + repoRoot);
      System.exit(1);
    }

    System.out.println("=== Transactional Analyzer ===");
    System.out.println("Repo root : " + repoRoot);
    System.out.println("Output    : " + outputFile.toAbsolutePath());
    System.out.println();

    // ── 1. Discover source roots ─────────────────────────────────────────────
    List<Path> sourceRoots = findSourceRoots(repoRoot);
    if (sourceRoots.isEmpty()) {
      System.err.println("ERROR: No src/main/java directories found under " + repoRoot);
      System.exit(1);
    }
    System.out.println("Source roots (" + sourceRoots.size() + "):");
    sourceRoots.forEach(r -> System.out.println("  " + r));
    System.out.println();

    // ── 2. Configure JavaParser symbol solver ────────────────────────────────
    configureJavaParser(sourceRoots);

    // ── 3. Index source files ────────────────────────────────────────────────
    System.out.println("[1/4] Indexing source files…");
    SourceIndexer indexer = new SourceIndexer();
    indexer.setRepoRoot(repoRoot);
    for (Path sourceRoot : sourceRoots) {
      indexer.indexSourceRoot(sourceRoot);
    }
    System.out.printf("      %d classes, %d methods, %d @Repository types%n%n",
        indexer.getClassCount(), indexer.getMethodCount(), indexer.getRepositoryCount());

    if (indexer.getRepositoryCount() == 0) {
      System.err.println("WARNING: No @Repository-annotated types found. "
          + "Make sure the source roots are correct.");
    }

    // ── 4. Build call graph ──────────────────────────────────────────────────
    System.out.println("[2/4] Building call graph…");
    CallGraphBuilder callGraph = new CallGraphBuilder(indexer);
    callGraph.build();
    System.out.printf("      %d call edges recorded%n%n", callGraph.getEdgeCount());

    // ── 5. Build caller trees ─────────────────────────────────────────────────
    System.out.println("[3/4] Building caller trees…");
    Map<String, List<CallerNode>> repoTrees = buildAllCallerTrees(indexer, callGraph);
    int totalTrees = repoTrees.values().stream().mapToInt(List::size).sum();
    System.out.printf("      %d method trees built%n%n", totalTrees);

    // ── 6. Generate HTML report ───────────────────────────────────────────────
    System.out.println("[4/4] Generating HTML report…");
    HtmlGenerator htmlGenerator = new HtmlGenerator();
    String html = htmlGenerator.generate(indexer, repoTrees);
    Files.writeString(outputFile, html);
    System.out.printf("      Written %,d bytes to %s%n%n", html.length(), outputFile.toAbsolutePath());
    System.out.println("Done. Open the HTML file in your browser.");
  }

  // ── Source root detection ────────────────────────────────────────────────────

  /**
   * Finds {@code src/main/java} directories within common Maven module directories, as well as
   * any discovered by a broader filesystem walk (for non-standard layouts).
   */
  private static List<Path> findSourceRoots(Path repoRoot) throws IOException {
    List<Path> roots = new ArrayList<>();

    // Walk up to 4 levels deep looking for src/main/java
    Files.walk(repoRoot, 4)
        .filter(p -> p.getFileName() != null)
        .filter(p -> p.endsWith("src/main/java") || p.toString().contains("/src/main/java"))
        .filter(Files::isDirectory)
        .filter(p -> {
          // Exclude target/ directories (compiled output that sometimes shadows sources)
          String s = p.toString();
          return !s.contains("/target/") && !s.contains("\\target\\");
        })
        .sorted()
        .forEach(roots::add);

    return roots;
  }

  // ── JavaParser configuration ─────────────────────────────────────────────────

  private static void configureJavaParser(List<Path> sourceRoots) {
    CombinedTypeSolver typeSolver = new CombinedTypeSolver();
    // JDK types (no full classpath — avoids loading Spring/JPA jars)
    typeSolver.add(new ReflectionTypeSolver(false));
    // Source-based solver for each module: enables cross-module type resolution
    for (Path sourceRoot : sourceRoots) {
      try {
        typeSolver.add(new JavaParserTypeSolver(sourceRoot.toFile()));
      } catch (Exception e) {
        System.err.println("  WARN: could not add type solver for " + sourceRoot + ": " + e.getMessage());
      }
    }

    JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
    ParserConfiguration config = new ParserConfiguration();
    config.setSymbolResolver(symbolSolver);
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    StaticJavaParser.setConfiguration(config);
  }

  // ── Caller tree construction ─────────────────────────────────────────────────

  /**
   * For every @Repository type found by the indexer, builds a list of root CallerNodes — one per
   * declared method. The tree grows upward: each node's {@code callers} list holds the methods
   * that invoke it.
   *
   * @return map: repository FQN → list of root CallerNodes (one per declared method)
   */
  private static Map<String, List<CallerNode>> buildAllCallerTrees(
      SourceIndexer indexer, CallGraphBuilder callGraph) {

    Map<String, List<CallerNode>> result = new LinkedHashMap<>();

    for (RepositoryInfo repo : indexer.getRepositories()) {
      List<CallerNode> methodTrees = new ArrayList<>();

      // Also collect callers of inherited JPA methods (findAll, save, etc.) that were
      // recorded in the call graph but may not be in declaredMethods.
      Set<MethodKey> allRepoKeys = new LinkedHashSet<>();
      repo.declaredMethods.forEach(m -> allRepoKeys.add(m.key));

      indexer.getMethodIndex().keySet().stream()
          .filter(k -> k.qualifiedClassName().equals(repo.qualifiedName))
          .forEach(allRepoKeys::add);

      // Also collect keys from the call graph that target this repository type but
      // aren't in the declared methods index (inherited JPA methods).
      // We identify these by looking at all callee keys that share the repository FQN.
      Set<String> declaredNames = new LinkedHashSet<>();
      repo.declaredMethods.forEach(m -> declaredNames.add(m.key.methodName()));

      for (MethodKey candidateCallee : new ArrayList<>(allRepoKeys)) {
        // Check if there are actually callers recorded for this key
        if (!callGraph.getCallersOf(candidateCallee).isEmpty()
            || !candidateCallee.methodName().isEmpty()) {
          allRepoKeys.add(candidateCallee);
        }
      }

      // For each method key, build a root node and its caller subtree
      for (MethodKey repoMethodKey : allRepoKeys) {
        MethodInfo rootInfo = indexer.getOrCreate(repoMethodKey);
        // Force isRepositoryMethod on the root
        if (!rootInfo.isRepositoryMethod) {
          rootInfo = new MethodInfo(rootInfo.key,
              rootInfo.isSpringTransactional, rootInfo.isJakartaTransactional,
              true, rootInfo.isHttpEndpoint,
              rootInfo.relativeFilePath, rootInfo.lineNumber,
              rootInfo.parameterSummary);
        }

        CallerNode root = new CallerNode(rootInfo);
        buildCallerSubtree(root, callGraph, indexer, new LinkedHashSet<>(), 0);
        methodTrees.add(root);
      }

      result.put(repo.qualifiedName, methodTrees);
    }

    return result;
  }

  /**
   * Recursively populates {@code node.callers} with the methods that call {@code node.method},
   * stopping at {@link #MAX_DEPTH} or when a cycle is detected.
   */
  private static void buildCallerSubtree(CallerNode node, CallGraphBuilder callGraph,
      SourceIndexer indexer, LinkedHashSet<MethodKey> ancestorKeys, int depth) {

    if (depth >= MAX_DEPTH) {
      node.callers.add(new CallerNode(node.method, false, true));
      return;
    }

    for (MethodKey callerKey : callGraph.getCallersOf(node.method.key)) {
      if (ancestorKeys.contains(callerKey)) {
        // Cycle detected
        node.callers.add(new CallerNode(indexer.getOrCreate(callerKey), true, false));
        continue;
      }

      CallerNode callerNode = new CallerNode(indexer.getOrCreate(callerKey));
      ancestorKeys.add(callerKey);
      buildCallerSubtree(callerNode, callGraph, indexer, ancestorKeys, depth + 1);
      ancestorKeys.remove(callerKey);
      node.callers.add(callerNode);
    }
  }
}
