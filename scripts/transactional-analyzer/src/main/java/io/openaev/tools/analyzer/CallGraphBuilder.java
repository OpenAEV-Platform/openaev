package io.openaev.tools.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.types.ResolvedType;
import io.openaev.tools.analyzer.SourceIndexer.ParsedFile;
import io.openaev.tools.analyzer.model.MethodKey;

import java.util.*;

/**
 * Second pass: visits every method body in the parsed source tree and records call edges.
 *
 * <p>The result is a reverse call graph: {@code callersOf(callee)} returns the set of methods that
 * call {@code callee} somewhere in their body.
 *
 * <p>Type resolution uses a two-tier strategy:
 * <ol>
 *   <li><b>JavaParser symbol solver</b> — when the solver is configured and succeeds, gives exact
 *       results even across modules.</li>
 *   <li><b>Name-based fallback</b> — resolves via field type declarations (handles the ubiquitous
 *       {@code private final XxxRepository xxxRepository} pattern used with Lombok
 *       {@code @RequiredArgsConstructor}).</li>
 * </ol>
 */
public class CallGraphBuilder {

  private final SourceIndexer indexer;

  /**
   * Reverse call graph: callee MethodKey → set of caller MethodKeys.
   * Using LinkedHashMap/LinkedHashSet for deterministic output order.
   */
  private final Map<MethodKey, Set<MethodKey>> callersOf = new LinkedHashMap<>();

  private int edgeCount = 0;

  public CallGraphBuilder(SourceIndexer indexer) {
    this.indexer = indexer;
  }

  public void build() {
    for (ParsedFile pf : indexer.getParsedFiles()) {
      try {
        processFile(pf);
      } catch (Exception e) {
        // A single broken file must not abort the whole analysis.
        System.err.printf("  WARN: call-graph pass skipping %s — %s%n",
            pf.filePath.getFileName(), e.getMessage());
      }
    }
  }

  // ── File processing ─────────────────────────────────────────────────────────

  private void processFile(ParsedFile pf) {
    CompilationUnit cu = pf.cu;
    String packageName = cu.getPackageDeclaration()
        .map(pd -> pd.getNameAsString())
        .orElse("");
    Map<String, String> imports = indexer.importsByFile.getOrDefault(
        pf.filePath.toString(), Map.of());

    for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
      String callerClass = packageName.isEmpty()
          ? type.getNameAsString()
          : packageName + "." + type.getNameAsString();

      // Field types for this class (pre-computed by SourceIndexer)
      Map<String, String> classFieldTypes =
          indexer.fieldTypesByClass.getOrDefault(callerClass, Map.of());

      for (MethodDeclaration method : type.getMethods()) {
        String callerMethod = method.getNameAsString();
        MethodKey callerKey = new MethodKey(callerClass, callerMethod, method.getParameters().size());

        method.accept(new CallVisitor(callerKey, callerClass, classFieldTypes, imports), null);
      }
    }
  }

  // ── Visitor ─────────────────────────────────────────────────────────────────

  private class CallVisitor extends VoidVisitorAdapter<Void> {

    private final MethodKey callerKey;
    private final String callerClass;
    private final Map<String, String> fieldTypes; // class-level fields
    private final Map<String, String> imports;
    /** Local variable types within the current method body. */
    private final Map<String, String> localVarTypes = new LinkedHashMap<>();

    CallVisitor(MethodKey callerKey, String callerClass,
        Map<String, String> fieldTypes, Map<String, String> imports) {
      this.callerKey = callerKey;
      this.callerClass = callerClass;
      this.fieldTypes = fieldTypes;
      this.imports = imports;
    }

    // Track local variable declarations so we can resolve their types.
    @Override
    public void visit(VariableDeclarationExpr expr, Void arg) {
      String rawType = SourceIndexer.stripGenerics(expr.getElementType().asString());
      String resolved = indexer.resolveTypeName(rawType, imports);
      for (VariableDeclarator var : expr.getVariables()) {
        localVarTypes.put(var.getNameAsString(), resolved);
      }
      super.visit(expr, arg);
    }

    @Override
    public void visit(MethodCallExpr call, Void arg) {
      String calleeClass = resolveCalleeClass(call);
      if (calleeClass != null) {
        String methodName = call.getNameAsString();
        MethodKey calleeKey = new MethodKey(calleeClass, methodName, call.getArguments().size());

        // Record edge only when callee is in our index or is a known @Repository type
        // (handles inherited JPA methods like findById, save, findAll, …)
        if (indexer.getMethodIndex().containsKey(calleeKey)
            || indexer.isRepositoryType(calleeClass)) {
          recordEdge(calleeKey, callerKey);
        }
      }
      super.visit(call, arg);
    }

    // ── Type resolution ────────────────────────────────────────────────────

    private String resolveCalleeClass(MethodCallExpr call) {
      Optional<Expression> scopeOpt = call.getScope();

      if (scopeOpt.isEmpty()) {
        // Unscoped call — treat as call on current class
        return callerClass;
      }

      Expression scope = scopeOpt.get();

      // Tier 1: symbol solver
      try {
        ResolvedType rt = scope.calculateResolvedType();
        if (rt.isReferenceType()) {
          return rt.asReferenceType().getQualifiedName();
        }
      } catch (Exception ignored) {
        // Fall through to name-based resolution
      }

      // Tier 2: name-based resolution
      return resolveExprType(scope);
    }

    private String resolveExprType(Expression expr) {
      if (expr instanceof NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        // Check local variables first, then class fields
        String type = localVarTypes.get(name);
        if (type != null) return type;
        return fieldTypes.get(name);
      }
      if (expr.isThisExpr()) {
        return callerClass;
      }
      // For chained calls (e.g. getRepository().save(…)) we give up gracefully
      return null;
    }
  }

  // ── Edge recording and accessors ────────────────────────────────────────────

  private void recordEdge(MethodKey callee, MethodKey caller) {
    // Avoid self-edges (direct recursion still recorded intentionally by omitting this guard)
    callersOf.computeIfAbsent(callee, k -> new LinkedHashSet<>()).add(caller);
    edgeCount++;
  }

  public Set<MethodKey> getCallersOf(MethodKey method) {
    return callersOf.getOrDefault(method, Set.of());
  }

  public int getEdgeCount() { return edgeCount; }
}
