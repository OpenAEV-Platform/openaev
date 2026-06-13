package io.openaev.tools.analyzer;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.openaev.tools.analyzer.model.CallerNode;
import io.openaev.tools.analyzer.model.MethodInfo;
import io.openaev.tools.analyzer.model.MethodKey;
import io.openaev.tools.analyzer.model.RepositoryInfo;
import io.openaev.tools.analyzer.view.BadgeView;
import io.openaev.tools.analyzer.view.MethodTreeView;
import io.openaev.tools.analyzer.view.NodeView;
import io.openaev.tools.analyzer.view.RepoView;

import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a self-contained HTML report by rendering a FreeMarker template
 * ({@code report.ftl}) with a pre-built view model.
 *
 * <p>All display logic (CSS class selection, badge construction, coverage analysis)
 * lives in this class. The template ({@code src/main/resources/report.ftl}) is purely
 * presentational.
 */
public class HtmlGenerator {

  /** Maximum caller-tree depth before inserting a depth-limit sentinel node. */
  private static final int MAX_DEPTH = 25;

  private final Configuration freemarker;

  public HtmlGenerator() {
    freemarker = new Configuration(Configuration.VERSION_2_3_33);
    freemarker.setClassLoaderForTemplateLoading(
        HtmlGenerator.class.getClassLoader(), "/");
    freemarker.setDefaultEncoding("UTF-8");
    freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    freemarker.setLogTemplateExceptions(false);
    // Expose public fields in addition to JavaBean getters
    DefaultObjectWrapperBuilder owb = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_33);
    owb.setExposeFields(true);
    freemarker.setObjectWrapper(owb.build());
  }

  // ── Public entry point ──────────────────────────────────────────────────────

  public String generate(SourceIndexer indexer, Map<String, List<CallerNode>> repoTrees) {
    List<RepositoryInfo> repos = indexer.getRepositories().stream()
        .sorted(Comparator.comparing(r -> r.relativeFilePath != null ? r.relativeFilePath : r.simpleName))
        .collect(Collectors.toList());

    long missingTx = repoTrees.values().stream()
        .flatMap(List::stream)
        .filter(root -> txCoverage(root) == TxCoverage.RED)
        .count();
    int depthLimitHits = repoTrees.values().stream()
        .flatMap(List::stream)
        .mapToInt(this::countDepthLimitNodes)
        .sum();
    int cycleCount = repoTrees.values().stream()
        .flatMap(List::stream)
        .mapToInt(this::countCyclicNodes)
        .sum();

    List<String> allOverrideTypes = collectAllOverrideTypes(repoTrees);

    List<RepoView> repoViews = repos.stream()
        .map(repo -> buildRepoView(repo, repoTrees.getOrDefault(repo.qualifiedName, List.of())))
        .collect(Collectors.toList());

    Map<String, Object> model = new LinkedHashMap<>();
    model.put("date", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(ZonedDateTime.now()));
    model.put("repos", repoViews);
    model.put("repoCount", repos.size());
    model.put("methodCount", repoTrees.values().stream().mapToInt(List::size).sum());
    model.put("missingTx", (int) missingTx);
    model.put("missingTxClass", missingTx > 0 ? "stat-danger" : "stat-ok");
    model.put("totalIndexedMethods", indexer.getMethodCount());
    model.put("classCount", indexer.getClassCount());
    model.put("depthLimitHits", depthLimitHits);
    model.put("depthLimitClass", depthLimitHits > 0 ? "stat-warn" : "stat-ok");
    model.put("cycleCount", cycleCount);
    model.put("cycleClass", cycleCount > 0 ? "stat-warn" : "stat-ok");
    model.put("maxDepth", MAX_DEPTH);
    model.put("allOverrideTypes", allOverrideTypes);
    model.put("css", loadResource("report.css"));
    model.put("js", loadResource("report.js"));

    try {
      Template template = freemarker.getTemplate("report.ftl");
      StringWriter out = new StringWriter();
      template.process(model, out);
      return out.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to render report template", e);
    }
  }

  // ── View model builders ─────────────────────────────────────────────────────

  private RepoView buildRepoView(RepositoryInfo repo, List<CallerNode> methodTrees) {
    TxCoverage repoTx = repoTxCoverage(methodTrees);
    EndpointCoverage repoEp = repoEndpointCoverage(methodTrees);

    List<MethodTreeView> methodViews = methodTrees.stream()
        .map(root -> buildMethodTreeView(root))
        .collect(Collectors.toList());

    return new RepoView(
        repo.htmlId(), repo.simpleName, repo.relativeFilePath,
        repoTx.cssClass, repoTx.tooltip,
        repoEp.cssClass, repoEp.tooltip,
        methodViews);
  }

  private MethodTreeView buildMethodTreeView(CallerNode root) {
    TxCoverage coverage = txCoverage(root);
    EndpointCoverage epCoverage = endpointCoverage(root);
    boolean neverCalled = root.callers.isEmpty() && !root.isCyclic && !root.isDepthLimitExceeded;
    String annTypes = String.join(",", collectNodeTypes(root));
    String overridesData = String.join(",", collectOverrideTypesInTree(root));

    List<NodeView> callerViews = root.callers.stream()
        .map(c -> buildNodeView(c, false))
        .collect(Collectors.toList());

    return new MethodTreeView(
        root.method.key.htmlId(),
        coverage.name().toLowerCase(),
        epCoverage.name().toLowerCase(),
        annTypes,
        overridesData,
        neverCalled,
        coverage.cssClass, coverage.tooltip,
        epCoverage.cssClass, epCoverage.tooltip,
        root.method.key.methodName(),
        root.method.parameterSummary,
        buildBadges(root.method, false),
        root.method.overridesFrom != null ? root.method.overridesFrom : "",
        callerViews);
  }

  private NodeView buildNodeView(CallerNode node, boolean isRoot) {
    if (node.isCyclic) {
      return new NodeView(true, false, "cyclic",
          node.method.key.simpleClassName(), node.method.key.methodName(),
          null, List.of(), null, 0, false, false, List.of());
    }
    if (node.isDepthLimitExceeded) {
      return new NodeView(false, true, "depth-limit",
          "", "", null, List.of(), null, 0, false, false, List.of());
    }
    String cssClass = nodeClass(node, isRoot);
    boolean indirect = "tx-indirect".equals(cssClass);
    List<NodeView> children = node.callers.stream()
        .map(c -> buildNodeView(c, false))
        .collect(Collectors.toList());
    return new NodeView(
        false, false, cssClass,
        node.method.key.simpleClassName(), node.method.key.methodName(),
        node.method.overridesFrom,
        buildBadges(node.method, indirect),
        node.method.relativeFilePath, node.method.lineNumber,
        node.method.hasSource(),
        node.method.isHttpEndpoint,
        children);
  }

  private List<BadgeView> buildBadges(MethodInfo m, boolean indirect) {
    List<BadgeView> badges = new ArrayList<>();
    if (m.isRepositoryMethod)    badges.add(new BadgeView("repo-method", "@Repository"));
    if (m.isSpringTransactional) badges.add(new BadgeView("tx-spring",   "@Transactional"));
    if (m.isJakartaTransactional) badges.add(new BadgeView("tx-jakarta",  "@Tx Jakarta"));
    if (!m.isTransactional() && !m.isRepositoryMethod && m.hasSource()) {
      badges.add(indirect
          ? new BadgeView("tx-indirect", "@Tx (indirect)")
          : new BadgeView("no-tx", "No @Tx"));
    }
    if (!m.hasSource())    badges.add(new BadgeView("external", "external"));
    if (m.isHttpEndpoint)  badges.add(new BadgeView("endpoint",  "@Endpoint"));
    return badges;
  }

  private String loadResource(String name) {
    try (var is = HtmlGenerator.class.getClassLoader().getResourceAsStream(name)) {
      if (is == null) throw new IllegalStateException("Resource not found: " + name);
      return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new RuntimeException("Failed to load resource: " + name, e);
    }
  }

  // ── CSS class selection ──────────────────────────────────────────────────────

  private String nodeClass(CallerNode node, boolean isRoot) {
    MethodInfo m = node.method;
    if (isRoot || m.isRepositoryMethod) return "repo-method";
    if (!m.hasSource()) return "external";
    if (m.isSpringTransactional) return "tx-spring";
    if (m.isJakartaTransactional) return "tx-jakarta";
    if (!node.callers.isEmpty() && allPathsCovered(node, false)) return "tx-indirect";
    return "no-tx";
  }

  // ── Node-type collection (annotation filter) ─────────────────────────────────

  private Set<String> collectNodeTypes(CallerNode root) {
    Set<String> types = new LinkedHashSet<>();
    // Include root's own annotation type (it can be @Transactional, no-tx, etc.)
    // but NOT "repo-method" since that's always true and would match every tree
    String rootClass = nodeClass(root, true);
    if (!"repo-method".equals(rootClass)) types.add(rootClass);
    // Then collect caller branch types
    for (CallerNode child : root.callers) {
      collectNodeTypesRecursive(child, false, types);
    }
    return types;
  }

  private void collectNodeTypesRecursive(CallerNode node, boolean isRoot, Set<String> types) {
    if (node.isCyclic) { types.add("cyclic"); return; }
    if (node.isDepthLimitExceeded) return;
    types.add(nodeClass(node, isRoot));
    for (CallerNode child : node.callers) {
      collectNodeTypesRecursive(child, false, types);
    }
  }

  // ── Override-type collection ─────────────────────────────────────────────────

  private List<String> collectAllOverrideTypes(Map<String, List<CallerNode>> repoTrees) {
    Set<String> all = new TreeSet<>();
    for (List<CallerNode> trees : repoTrees.values()) {
      for (CallerNode root : trees) {
        all.addAll(collectOverrideTypesInTree(root));
      }
    }
    return new ArrayList<>(all);
  }

  private Set<String> collectOverrideTypesInTree(CallerNode root) {
    Set<String> types = new LinkedHashSet<>();
    collectOverrideTypesRecursive(root, types);
    return types;
  }

  private void collectOverrideTypesRecursive(CallerNode node, Set<String> types) {
    if (node.isCyclic || node.isDepthLimitExceeded) return;
    if (node.method.overridesFrom != null) {
      for (String t : node.method.overridesFrom.split(",")) {
        types.add(t.trim());
      }
    }
    for (CallerNode child : node.callers) {
      collectOverrideTypesRecursive(child, types);
    }
  }

  // ── Depth limit counter ──────────────────────────────────────────────────────

  private int countDepthLimitNodes(CallerNode node) {
    if (node.isDepthLimitExceeded) return 1;
    int count = 0;
    for (CallerNode child : node.callers) count += countDepthLimitNodes(child);
    return count;
  }

  private int countCyclicNodes(CallerNode node) {
    if (node.isCyclic) return 1;
    int count = 0;
    for (CallerNode child : node.callers) count += countCyclicNodes(child);
    return count;
  }

  // ── Repo-level coverage aggregation ─────────────────────────────────────────

  private TxCoverage repoTxCoverage(List<CallerNode> trees) {
    if (trees.isEmpty()) return TxCoverage.GREY;
    boolean allGrey = true;
    for (CallerNode root : trees) {
      TxCoverage c = txCoverage(root);
      if (c == TxCoverage.RED) return TxCoverage.RED;
      if (c != TxCoverage.GREY) allGrey = false;
    }
    return allGrey ? TxCoverage.GREY : TxCoverage.GREEN;
  }

  private EndpointCoverage repoEndpointCoverage(List<CallerNode> trees) {
    if (trees.isEmpty()) return EndpointCoverage.GREY;
    boolean allGrey = true;
    for (CallerNode root : trees) {
      EndpointCoverage c = endpointCoverage(root);
      if (c == EndpointCoverage.VIOLET) return EndpointCoverage.VIOLET;
      if (c != EndpointCoverage.GREY) allGrey = false;
    }
    return allGrey ? EndpointCoverage.GREY : EndpointCoverage.BLUE;
  }

  // ── @Transactional coverage analysis ────────────────────────────────────────

  private enum TxCoverage {
    GREEN("tx-dot-green", "All call paths are covered by @Transactional"),
    RED  ("tx-dot-red",   "One or more call paths have no @Transactional"),
    GREY ("tx-dot-grey",  "Method is never called in indexed source");

    final String cssClass;
    final String tooltip;
    TxCoverage(String cssClass, String tooltip) { this.cssClass = cssClass; this.tooltip = tooltip; }
  }

  private TxCoverage txCoverage(CallerNode root) {
    if (root.callers.isEmpty() && !root.isCyclic && !root.isDepthLimitExceeded) return TxCoverage.GREY;
    return allPathsCovered(root, false) ? TxCoverage.GREEN : TxCoverage.RED;
  }

  private boolean allPathsCovered(CallerNode node, boolean txSeenAbove) {
    if (node.isCyclic || node.isDepthLimitExceeded) return true;
    boolean txNow = txSeenAbove || node.method.isTransactional();
    if (node.callers.isEmpty()) return txNow;
    for (CallerNode child : node.callers) {
      if (!allPathsCovered(child, txNow)) return false;
    }
    return true;
  }

  // ── Endpoint origin analysis ─────────────────────────────────────────────────

  private enum EndpointCoverage {
    BLUE  ("ep-dot-blue",   "All call paths originate from an @Endpoint"),
    VIOLET("ep-dot-violet", "One or more call paths do not originate from an @Endpoint"),
    GREY  ("tx-dot-grey",   "Method is never called in indexed source");

    final String cssClass;
    final String tooltip;
    EndpointCoverage(String cssClass, String tooltip) { this.cssClass = cssClass; this.tooltip = tooltip; }
  }

  private EndpointCoverage endpointCoverage(CallerNode root) {
    if (root.callers.isEmpty() && !root.isCyclic && !root.isDepthLimitExceeded) return EndpointCoverage.GREY;
    return allLeavesAreEndpoints(root) ? EndpointCoverage.BLUE : EndpointCoverage.VIOLET;
  }

  private boolean allLeavesAreEndpoints(CallerNode node) {
    if (node.isCyclic || node.isDepthLimitExceeded) return true;
    if (node.callers.isEmpty()) return node.method.isHttpEndpoint;
    for (CallerNode child : node.callers) {
      if (!allLeavesAreEndpoints(child)) return false;
    }
    return true;
  }
}
