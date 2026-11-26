package io.openaev.config;

import static io.openaev.config.ScopeStatementInspector.LOGGED_STATEMENT_MAX_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.parser.feature.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

/**
 * Covers the generic, dimension-driven rewriting {@link ScopeStatementInspector} adds on top of the
 * tenant-only behaviour pinned by {@link TenantStatementInspectorTest}: that a single dimension
 * emits exactly its own predicate (so the tenant extraction changed nothing), and that a second
 * dimension is ANDed onto the same tables rather than replacing the first.
 *
 * <p>The second dimension here is a stand-in, not the marking one: it exercises the composition
 * contract without depending on marking definitions existing.
 */
@DisplayName("ScopeStatementInspector")
class ScopeStatementInspectorTest {

  private static final TenantTables TABLES =
      new TenantTables(Set.of("documents"), Set.of("groups"));

  /** A minimal second dimension: a bare predicate on one table, no write attribution. */
  private record LabelDimension(Set<String> tables) implements ScopeDimension {
    @Override
    public String name() {
      return "label";
    }

    @Override
    public Set<String> activeTables() {
      return tables;
    }

    @Override
    public boolean covers(String table) {
      return tables.contains(table.toLowerCase());
    }

    @Override
    public String readPredicate(String table, String alias) {
      return "can_access_label(" + alias + ".label_id, true)";
    }

    @Override
    public String writePredicate(String table, String alias) {
      return "can_access_label(" + alias + ".label_id)";
    }
  }

  /**
   * A dimension on {@code documents} whose predicate is fixed text, or which fails with an
   * exception that carries no message when the text is null. A non-null guard is the fixed text
   * that wraps a value written to {@code tenant_id}.
   */
  private record FixedPredicateDimension(String predicate, String guard) implements ScopeDimension {
    @Override
    public String name() {
      return "fixed";
    }

    @Override
    public Set<String> activeTables() {
      return Set.of("documents");
    }

    @Override
    public boolean covers(String table) {
      return "documents".equalsIgnoreCase(table);
    }

    @Override
    public String readPredicate(String table, String alias) {
      return writePredicate(table, alias);
    }

    @Override
    public String writePredicate(String table, String alias) {
      if (predicate == null) {
        throw new IllegalStateException();
      }
      return predicate;
    }

    @Override
    public String writeAttributionColumn() {
      return guard == null ? null : "tenant_id";
    }

    @Override
    public boolean guardsWrittenValue(String table) {
      return guard != null;
    }

    @Override
    public String guardedWrittenValue(String table, String valueExpression) {
      return guard;
    }
  }

  private static String inspect(ScopeStatementInspector inspector, String sql) {
    return inspector.inspect(sql).replaceAll("\\s+", " ").trim();
  }

  private static int occurrences(String text, String part) {
    return text.split(Pattern.quote(part), -1).length - 1;
  }

  @Nested
  @DisplayName("with a single dimension")
  class SingleDimension {

    private final ScopeStatementInspector inspector =
        new ScopeStatementInspector(List.of(new TenantDimension(TABLES)));

    @Test
    @DisplayName("emits that dimension's predicate alone, with no AND wrapper")
    void singleDimensionEmitsItsPredicateVerbatim() {
      // The extraction must not change the emitted SQL: an AND-join of one element is the element.
      // documents is strict, so no allow_platform flag either. No pre-existing WHERE, so there is
      // nothing for the predicate to be ANDed onto — narrowedPredicateAddedOnceWithExistingWhere
      // in TenantStatementInspectorTest covers the existing-WHERE case.
      String out = inspect(inspector, "SELECT * FROM documents d");
      assertTrue(out.contains("WHERE can_access_tenant(d.tenant_id)"), out);
      assertTrue(!out.contains("AND can_access_tenant"), out);
    }

    @Test
    @DisplayName("produces the same SQL as the tenant-only inspector")
    void matchesTheTenantOnlyInspector() {
      TenantStatementInspector tenantOnly = new TenantStatementInspector(TABLES);
      for (String sql :
          List.of(
              "SELECT * FROM documents d WHERE d.id = ?",
              "SELECT * FROM documents d JOIN groups g ON g.id = d.group_id",
              "UPDATE documents SET name = ? WHERE id = ?",
              "DELETE FROM documents WHERE id = ?")) {
        assertEquals(tenantOnly.inspect(sql), inspector.inspect(sql), sql);
      }
    }

    @Test
    @DisplayName("filters each table of a WITH query exactly once")
    void filtersEachTableOfAWithQueryOnce() {
      String out =
          inspect(
              inspector,
              "WITH docs AS (SELECT d.group_id FROM documents d WHERE d.id = ?) "
                  + "SELECT g.id FROM groups g JOIN docs ON docs.group_id = g.id");
      assertEquals(1, occurrences(out, "can_access_tenant(d.tenant_id)"), out);
      assertEquals(1, occurrences(out, "can_access_tenant(g.tenant_id, true)"), out);
    }
  }

  @Nested
  @DisplayName("with two dimensions")
  class TwoDimensions {

    private final ScopeStatementInspector inspector =
        new ScopeStatementInspector(
            List.of(new TenantDimension(TABLES), new LabelDimension(Set.of("documents"))));

    @Test
    @DisplayName("ANDs both predicates on a table both dimensions cover")
    void andsBothPredicatesOnACommonTable() {
      String out = inspect(inspector, "SELECT * FROM documents d WHERE d.id = ?");
      assertTrue(
          out.contains("can_access_tenant(d.tenant_id) AND can_access_label(d.label_id, true)"),
          out);
    }

    @Test
    @DisplayName("applies only the covering dimension on a table the other does not cover")
    void appliesOnlyTheCoveringDimension() {
      // groups is tenant-scoped but carries no label, so only the tenant predicate applies.
      String out = inspect(inspector, "SELECT * FROM groups g WHERE g.id = ?");
      assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
      assertTrue(!out.contains("can_access_label"), out);
    }

    @Test
    @DisplayName("ANDs both write predicates into the WHERE of an UPDATE")
    void andsBothWritePredicatesOnUpdate() {
      String out = inspect(inspector, "UPDATE documents SET name = ? WHERE id = ?");
      assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
      assertTrue(out.contains("can_access_label(documents.label_id)"), out);
    }

    @Test
    @DisplayName("a table active only in the second dimension still trips the gate")
    void secondDimensionContributesToTheGate() {
      // The gate is the union of both dimensions' tables; a table only the label dimension knows
      // must not slip through unfiltered.
      ScopeStatementInspector labelOnly =
          new ScopeStatementInspector(
              List.of(
                  new TenantDimension(new TenantTables(Set.of(), Set.of())),
                  new LabelDimension(Set.of("reports"))));
      String out = inspect(labelOnly, "SELECT * FROM reports r WHERE r.id = ?");
      assertTrue(out.contains("can_access_label(r.label_id, true)"), out);
    }
  }

  @Nested
  @DisplayName("with no active table")
  class Inert {

    @Test
    @DisplayName("passes statements through untouched")
    void inertWhenNothingIsActive() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(
              List.of(new TenantDimension(new TenantTables(Set.of(), Set.of()))));
      String sql = "SELECT * FROM documents d WHERE d.id = ?";
      assertEquals(sql, inspector.inspect(sql));
    }

    @Test
    @DisplayName("an empty dimension list is inert")
    void inertWithNoDimensions() {
      ScopeStatementInspector inspector = new ScopeStatementInspector(List.of());
      String sql = "SELECT * FROM documents d WHERE d.id = ?";
      assertEquals(sql, inspector.inspect(sql));
    }
  }

  @Nested
  @DisplayName("parsing a statement")
  class Parsing {

    /**
     * Each line: a statement of TenantStatementInspectorTest, a tab, the SQL it is rewritten to.
     */
    private static final String PARITY_STATEMENTS = "/inspector/rewrite-parity.tsv";

    private final ScopeStatementInspector inspector =
        new TenantStatementInspector(
            new TenantTables(Set.of("documents", "findings"), Set.of("groups")));

    private final Logger inspectorLogger =
        (Logger) LoggerFactory.getLogger(ScopeStatementInspector.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level configuredLevel;

    // The level production sets for this logger in application.properties: a test JVM that has
    // started a Spring context leaves it at the test root level, which is ERROR.
    @BeforeEach
    void captureInspectorLogs() {
      configuredLevel = inspectorLogger.getLevel();
      inspectorLogger.setLevel(Level.WARN);
      appender.start();
      inspectorLogger.addAppender(appender);
    }

    @AfterEach
    void releaseInspectorLogs() {
      inspectorLogger.detachAppender(appender);
      appender.stop();
      inspectorLogger.setLevel(configuredLevel);
    }

    static Stream<Arguments> parityStatements() throws IOException {
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(
                  Objects.requireNonNull(
                      ScopeStatementInspectorTest.class.getResourceAsStream(PARITY_STATEMENTS)),
                  StandardCharsets.UTF_8))) {
        return reader.lines().map(line -> line.split("\t", 2)).map(Arguments::of).toList().stream();
      }
    }

    @ParameterizedTest
    @MethodSource("parityStatements")
    @DisplayName("rewrites each statement of the inspector tests to the same SQL")
    void given_inspectorTestStatement_should_rewriteToTheRecordedSql(
        String statement, String rewritten) {
      // Act
      String out = inspector.inspect(statement);

      // Assert
      assertEquals(rewritten, out);
    }

    @Test
    @DisplayName(
        "parses on a caller thread that carries an interrupt flag, and leaves the flag set")
    void given_interruptedCallerThread_should_parseAndRewriteTheStatement() {
      // Arrange
      Thread.currentThread().interrupt();
      String out;
      boolean interruptedAfter;

      // Act
      try {
        out = inspect(inspector, "SELECT * FROM documents d WHERE d.id = ?");
      } finally {
        interruptedAfter = Thread.interrupted();
      }

      // Assert
      assertTrue(out.contains("AND (can_access_tenant(d.tenant_id))"), out);
      assertTrue(interruptedAfter, "the caller's interrupt flag belongs to the caller");
      assertTrue(appender.list.isEmpty(), "a parsed statement logs nothing");
    }

    @Test
    @DisplayName("refuses a statement it cannot parse and logs it once, string literals redacted")
    void given_unparseableStatement_should_refuseAndLogTheRedactedStatement() {
      // Arrange
      String statement =
          "SELECT * FROM documents d WHERE d.name = 'first-literal' 'second-literal'";

      // Act
      assertThrows(TenantFilteringException.class, () -> inspector.inspect(statement));

      // Assert
      assertEquals(1, appender.list.size(), "one log line per refusal");
      ILoggingEvent event = appender.list.getFirst();
      String logged = event.getFormattedMessage();
      assertEquals(Level.WARN, event.getLevel());
      assertTrue(
          logged.contains("SELECT * FROM documents d WHERE d.name = ? ?"),
          "the statement is logged with its literals redacted: " + logged);
      assertTrue(logged.contains("ParseException"), "the cause class is logged: " + logged);
      assertTrue(logged.contains("[tenant]"), "the scope dimensions are logged: " + logged);
      assertTrue(
          !logged.contains("first-literal") && !logged.contains("second-literal"),
          "no literal value reaches the log: " + logged);
    }

    /** The statement part of a refusal line: the hash, then the logged text up to the end. */
    private static final Pattern LOGGED_STATEMENT =
        Pattern.compile("; statement ([0-9a-f]{12}): (.*)\\z", Pattern.DOTALL);

    /** A statement whose simple parsing pass fails and whose complex pass succeeds. */
    private static final String COMPLEX_ONLY_EXPRESSION =
        "CASE WHEN d.a = 1 THEN d.b > 1 ELSE false END";

    private String singleRefusal() {
      assertEquals(1, appender.list.size(), "one log line per refusal");
      ILoggingEvent event = appender.list.getFirst();
      assertEquals(Level.WARN, event.getLevel());
      return event.getFormattedMessage();
    }

    private Matcher loggedStatement(ILoggingEvent event) {
      Matcher matcher = LOGGED_STATEMENT.matcher(event.getFormattedMessage());
      assertTrue(matcher.find(), "a statement hash and text: " + event.getFormattedMessage());
      return matcher;
    }

    @Test
    @DisplayName(
        "caps a long logged statement, names its full length, and hashes the redacted statement")
    void given_longRefusedStatement_should_logItCappedWithItsLengthAndHash() {
      // Arrange
      String inList = "SELECT * FROM documents d WHERE d.id IN (" + "?, ".repeat(1000) + "?)";
      String statement = inList + " 'first-literal' 'second-literal'";
      String sameShapeOtherValues = inList + " 'other-literal' 'last-literal'";
      String otherStatement = inList + " AND d.name = 'first-literal' 'second-literal'";
      String redacted = inList + " ? ?";

      // Act
      for (String sql : List.of(statement, sameShapeOtherValues, otherStatement)) {
        assertThrows(TenantFilteringException.class, () -> inspector.inspect(sql));
      }

      // Assert
      assertEquals(3, appender.list.size(), "one log line per refusal");
      Matcher first = loggedStatement(appender.list.get(0));
      Matcher sameShape = loggedStatement(appender.list.get(1));
      Matcher other = loggedStatement(appender.list.get(2));
      assertEquals(
          redacted.substring(0, LOGGED_STATEMENT_MAX_LENGTH)
              + "... [truncated, "
              + redacted.length()
              + " chars]",
          first.group(2));
      assertEquals(first.group(1), sameShape.group(1), "one statement shape, one hash");
      assertNotEquals(first.group(1), other.group(1), "another statement, another hash");
    }

    static Stream<Arguments> refusalsWithAnAwkwardLiteral() {
      return Stream.of(
          // The parser escapes a line break in its own messages; the step message that names the
          // written value carries it as it is.
          Arguments.of(
              new ScopeStatementInspector(
                  List.of(new FixedPredicateDimension("true", "broken ((("))),
              "UPDATE documents SET tenant_id = 'multi-line\nsecret-literal' WHERE id = ?"),
          Arguments.of(
              new TenantStatementInspector(
                  new TenantTables(Set.of("documents", "findings"), Set.of("groups"))),
              "SELECT * FROM documents d WHERE d.name = 'never-closed secret-literal"));
    }

    @ParameterizedTest
    @MethodSource("refusalsWithAnAwkwardLiteral")
    @DisplayName("logs no text of a literal that spans two lines or is not terminated")
    void given_refusedStatementWithAnAwkwardLiteral_should_logNoTextOfIt(
        ScopeStatementInspector refusing, String statement) {
      // Act
      assertThrows(TenantFilteringException.class, () -> refusing.inspect(statement));

      // Assert
      String logged = singleRefusal();
      for (String part : List.of("multi-line", "secret", "never-closed")) {
        assertFalse(logged.contains(part), "no literal text reaches the log: " + logged);
      }
    }

    /** Literal text no refusal line may carry. */
    private static final String SECRET = "secret-value";

    static Stream<Arguments> refusalsWithPostgresqlLexicalForms() {
      return Stream.of(
          Arguments.of(
              "dollar-quoted literal",
              "SELECT * FROM documents d WHERE d.name = $$" + SECRET + "$$ AND ("),
          Arguments.of(
              "tagged dollar-quoted literal",
              "SELECT * FROM documents d WHERE d.name = $tag$" + SECRET + "$tag$ AND ("),
          Arguments.of(
              "escape string with a backslash-escaped quote",
              "SELECT * FROM documents d WHERE d.name = E'it\\'s " + SECRET + "' AND ("),
          Arguments.of(
              "line comment holding an apostrophe",
              "SELECT * FROM documents d -- it's a note\nWHERE d.name = '" + SECRET + "' AND ("),
          Arguments.of(
              "block comment holding an apostrophe",
              "SELECT * FROM documents d /* it's */ WHERE d.name = '" + SECRET + "' AND ("),
          Arguments.of(
              "quoted identifier holding an apostrophe",
              "SELECT d.id FROM documents d WHERE d.\"it's\" = '" + SECRET + "' AND ("),
          Arguments.of(
              "unterminated dollar-quoted literal",
              "SELECT * FROM documents d WHERE d.name = $$" + SECRET + " AND ("),
          Arguments.of(
              "unterminated block comment",
              "SELECT * FROM documents d WHERE d.name = 'a' /* " + SECRET),
          Arguments.of(
              "dollar-quoted literal of 1 MB",
              "SELECT * FROM documents d WHERE d.name = $$"
                  + SECRET.repeat(1024 * 1024 / SECRET.length())
                  + "$$ AND ("));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusalsWithPostgresqlLexicalForms")
    @DisplayName("logs no literal text of a statement written with a PostgreSQL lexical form")
    void given_refusedStatementWithAPostgresqlLexicalForm_should_logNoLiteralText(
        String form, String statement) {
      // Act
      assertThrows(TenantFilteringException.class, () -> inspector.inspect(statement));

      // Assert
      String logged = singleRefusal();
      assertFalse(logged.contains("secret"), form + ": no literal text in the log: " + logged);
      assertTrue(logged.contains("FROM documents d"), form + ": the table is logged: " + logged);
    }

    @Test
    @DisplayName("names the step that refused the statement next to its root cause")
    void given_refusalAtARewriteStep_should_logTheStepAndTheRootCause() {
      // Arrange
      ScopeStatementInspector unparseablePredicate =
          new ScopeStatementInspector(List.of(new FixedPredicateDimension("broken (((", null)));

      // Act
      assertThrows(
          TenantFilteringException.class,
          () -> unparseablePredicate.inspect("DELETE FROM documents WHERE id = ?"));

      // Assert
      String logged = singleRefusal();
      assertTrue(
          logged.contains(
              "dimensions [fixed]: could not add the tenant filter to the WHERE clause; root cause: "
                  + ParseException.class.getName()
                  + ": "),
          logged);
    }

    @Test
    @DisplayName("logs the class alone of a root cause that carries no message")
    void given_rootCauseWithoutMessage_should_logItsClassOnly() {
      // Arrange
      ScopeStatementInspector failingPredicate =
          new ScopeStatementInspector(List.of(new FixedPredicateDimension(null, null)));

      // Act
      assertThrows(
          TenantFilteringException.class,
          () -> failingPredicate.inspect("SELECT * FROM documents d"));

      // Assert
      String logged = singleRefusal();
      assertTrue(
          logged.contains(
              "refusing SQL the tenant filter could not process; root cause: "
                  + IllegalStateException.class.getName()
                  + "; statement "),
          logged);
    }

    @Test
    @DisplayName("logs a statement shape it refuses on purpose once, with the exception class")
    void given_statementShapeRefusedOnPurpose_should_logOneWarning() {
      // Act
      assertThrows(
          TenantFilteringException.class, () -> inspector.inspect("TRUNCATE TABLE documents"));

      // Assert
      String logged = singleRefusal();
      assertTrue(
          logged.contains(
              "statement shape not supported by tenant filtering: Truncate; root cause: "
                  + TenantFilteringException.class.getName()
                  + "; statement "),
          logged);
      assertTrue(logged.endsWith(": TRUNCATE TABLE documents"), logged);
    }

    @Test
    @DisplayName("rewrites a statement that only the complex parsing pass accepts")
    void given_statementOnlyComplexParsingAccepts_should_rewriteIt() {
      // Arrange
      String statement = "SELECT " + COMPLEX_ONLY_EXPRESSION + " FROM documents d";
      assertThrows(
          ParseException.class,
          () -> CCJSqlParserUtil.newParser(statement).withAllowComplexParsing(false).Statement());

      // Act
      String out = inspect(inspector, statement);

      // Assert: the SQL the inspector produces with CCJSqlParserUtil.parse
      assertEquals(
          "SELECT "
              + COMPLEX_ONLY_EXPRESSION
              + " FROM documents d WHERE can_access_tenant(d.tenant_id)",
          out);
      assertTrue(appender.list.isEmpty(), "a parsed statement logs nothing");
    }

    @Test
    @DisplayName(
        "refuses a statement nested past the allowed depth that the simple pass rejects, like"
            + " the library parse")
    void given_statementNestedPastTheAllowedDepth_should_refuseItWithoutTheComplexPass() {
      // Arrange
      int allowedDepth = (Integer) Feature.allowedNestingDepth.getDefaultValue();
      String statement =
          "SELECT "
              + "f(".repeat(allowedDepth + 1)
              + COMPLEX_ONLY_EXPRESSION
              + ")".repeat(allowedDepth + 1)
              + " FROM documents d";
      assertTrue(CCJSqlParserUtil.getNestingDepth(statement) > allowedDepth);
      assertThrows(JSQLParserException.class, () -> CCJSqlParserUtil.parse(statement));

      // Act
      assertThrows(TenantFilteringException.class, () -> inspector.inspect(statement));

      // Assert
      assertTrue(singleRefusal().contains(ParseException.class.getName()));
    }
  }
}
