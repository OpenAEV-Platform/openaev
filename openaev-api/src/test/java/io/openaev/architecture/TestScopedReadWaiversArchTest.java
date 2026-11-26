package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.ExpectsEmptyScopeReads;
import io.openaev.config.WaivesUnscopedReads;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Every test that carries {@link ExpectsEmptyScopeReads} or {@link WaivesUnscopedReads} is listed
 * in {@value #RESOURCE}, and the list only shrinks. The first switches the fail-closed gate off for
 * the whole test, so a test that carries it reports no unscoped production read, those outside its
 * subject included; the second waives named reads for that test only. Either one is a review call
 * made in the diff of this file, and the frozen size makes a removed annotation lower {@link #SIZE}
 * in the same change, so the freed line cannot be taken again silently.
 *
 * <p>A line names a class ({@code io.openaev.Foo}, nested classes with {@code $}) or a method
 * ({@code io.openaev.Foo$Bar#method}). Lines starting with {@code #} and blank lines are ignored.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("The tests that waive unscoped reads for themselves are listed")
class TestScopedReadWaiversArchTest {

  static final String RESOURCE = "/test-scoped-read-waivers.txt";

  /** The number of lines of {@value #RESOURCE}. Lower it, never raise it without a review. */
  static final int SIZE = 28;

  private static final List<Class<? extends Annotation>> WAIVERS =
      List.of(ExpectsEmptyScopeReads.class, WaivesUnscopedReads.class);

  private Set<String> annotated;
  private Set<String> scanned;
  private List<String> listed;

  @BeforeAll
  void scanTheTestTree() throws IOException {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("io.openaev");
    annotated = new TreeSet<>();
    scanned = new TreeSet<>();
    for (JavaClass javaClass : classes) {
      scanned.add(javaClass.getName());
      if (WAIVERS.stream().anyMatch(javaClass::isAnnotatedWith)) {
        annotated.add(javaClass.getName());
      }
      javaClass.getMethods().stream()
          .filter(method -> WAIVERS.stream().anyMatch(method::isAnnotatedWith))
          .forEach(method -> annotated.add(javaClass.getName() + "#" + method.getName()));
    }
    listed = load();
  }

  @Test
  @DisplayName("given the test tree, should reach this guard and the annotation it lists")
  void given_testTree_should_reachThisGuardAndTheAnnotation() {
    // Assert: a scan that misses them read the wrong tree, so its silence means nothing
    assertThat(scanned)
        .contains(TestScopedReadWaiversArchTest.class.getName())
        .contains(ExpectsEmptyScopeReads.class.getName())
        .contains(WaivesUnscopedReads.class.getName());
  }

  @Test
  @DisplayName("given an annotated test, should find it listed")
  void given_annotatedTest_should_findItListed() {
    // Assert
    assertThat(new TreeSet<>(annotated))
        .as(
            "Tests carrying @ExpectsEmptyScopeReads or @WaivesUnscopedReads not listed in %s."
                + " Give the caller a membership or a scoped fixture; if the empty read is the"
                + " subject, list the test and raise SIZE in review",
            RESOURCE)
        .isSubsetOf(listed);
  }

  @Test
  @DisplayName("given a listed test, should find it annotated")
  void given_listedTest_should_findItAnnotated() {
    // Assert
    assertThat(listed)
        .as("Lines of %s whose test does not carry the annotation: remove them", RESOURCE)
        .isSubsetOf(annotated)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("given the list, should hold exactly the frozen number of lines")
  void given_theList_should_holdTheFrozenNumberOfLines() {
    // Assert
    assertThat(listed)
        .as("the size of %s is frozen: lower SIZE with every line removed", RESOURCE)
        .hasSize(SIZE);
  }

  private static List<String> load() throws IOException {
    List<String> lines = new ArrayList<>();
    try (InputStream in = TestScopedReadWaiversArchTest.class.getResourceAsStream(RESOURCE)) {
      assertThat(in).as("%s must exist", RESOURCE).isNotNull();
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          lines.add(trimmed);
        }
      }
    }
    return lines;
  }
}
