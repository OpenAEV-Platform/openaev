package io.openaev.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Keeps {@link DocsLinks} equal to {@code docs/links.json}, the map the docs build checks, and the
 * docs URLs of the {@code application.properties} comments among its links.
 */
@DisplayName("DocsLinks Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocsLinksTest {

  private static final String LINKS_FILE = "docs/links.json";
  private static final String PROPERTIES_FILE =
      "openaev-api/src/main/resources/application.properties";
  private static final String SOURCE_FILE =
      "openaev-api/src/main/java/io/openaev/docs/DocsLinks.java";

  /** The docs domain, however it is spelled. */
  private static final Pattern DOCS_DOMAIN =
      Pattern.compile("docs\\.openaev\\.io", Pattern.CASE_INSENSITIVE);

  /** A docs URL in prose, with or without a scheme, up to the next whitespace. */
  private static final Pattern DOCS_URL =
      Pattern.compile("(https?://)?docs\\.openaev\\.io\\S*", Pattern.CASE_INSENSITIVE);

  /** A Java string literal on one line. */
  private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\r\\n]|\\\\.)*)\"");

  /** Punctuation that may follow a URL in prose without belonging to it. */
  private static final String TRAILING_PUNCTUATION = ".,;:!?)]}>'\"";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Path repositoryRoot;
  private Map<String, String> urls;

  /**
   * The repository root: the first directory holding docs/links.json, from the working directory
   * up. Maven runs the tests from the module directory, IDEs from the module or the repository.
   */
  private static Path findRepositoryRoot() {
    for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve(LINKS_FILE))) {
        return dir;
      }
    }
    throw new IllegalStateException(
        LINKS_FILE + " not found from " + Path.of("").toAbsolutePath() + " or its parents");
  }

  /**
   * The full URL of each key of the links map: {@code base} followed by the key's path. Other
   * top-level members (e.g. {@code retired}, for the docs build) are ignored.
   */
  private static Map<String, String> readUrls(String json) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    String base = root.get("base").asText();
    Map<String, String> urls = new TreeMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = root.get("links").fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> link = it.next();
      urls.put(link.getKey(), base + link.getValue().asText());
    }
    return urls;
  }

  /** The members of DocsLinks, without those a coverage agent adds. */
  private static <T extends Member> List<T> declared(T[] members) {
    return Arrays.stream(members).filter(member -> !member.isSynthetic()).toList();
  }

  private static Map<String, String> constants() throws IllegalAccessException {
    Map<String, String> constants = new TreeMap<>();
    for (Field field : declared(DocsLinks.class.getDeclaredFields())) {
      constants.put(field.getName(), (String) field.get(null));
    }
    return constants;
  }

  private static int lineOf(String text, int index) {
    return (int) text.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
  }

  private static String stripTrailingPunctuation(String url) {
    int end = url.length();
    while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
      end--;
    }
    return url.substring(0, end);
  }

  @BeforeAll
  void readLinksMap() throws IOException {
    repositoryRoot = findRepositoryRoot();
    urls = readUrls(Files.readString(repositoryRoot.resolve(LINKS_FILE), StandardCharsets.UTF_8));
  }

  @Nested
  @DisplayName("When compared with docs/links.json")
  class ComparedWithLinksMap {

    @Test
    @DisplayName("Every key has a constant holding the base URL followed by its path")
    void given_linksMap_should_haveOneConstantPerKey() throws IllegalAccessException {
      // Arrange
      Map<String, String> expected = new TreeMap<>();
      urls.forEach((key, url) -> expected.put(key.toUpperCase(Locale.ROOT), url));

      // Act
      Map<String, String> constants = constants();

      // Assert
      assertThat(constants).containsAllEntriesOf(expected);
    }

    @Test
    @DisplayName("Every constant is a key of the map")
    void given_constants_should_allBeKeysOfTheMap() throws IllegalAccessException {
      // Arrange
      Collection<String> expectedNames =
          urls.keySet().stream().map(key -> key.toUpperCase(Locale.ROOT)).toList();

      // Act
      Collection<String> names = constants().keySet();

      // Assert
      assertThat(names).containsExactlyInAnyOrderElementsOf(expectedNames);
    }
  }

  @Nested
  @DisplayName("When reading the DocsLinks class")
  class DocsLinksClass {

    @Test
    @DisplayName("It holds only public static final String constants and its private constructor")
    void given_docsLinksClass_should_holdOnlyStringConstants() {
      // Act
      List<Field> fields = declared(DocsLinks.class.getDeclaredFields());
      List<Constructor<?>> constructors = declared(DocsLinks.class.getDeclaredConstructors());

      // Assert
      assertThat(fields)
          .allSatisfy(
              field -> {
                int modifiers = field.getModifiers();
                assertThat(
                        Modifier.isPublic(modifiers)
                            && Modifier.isStatic(modifiers)
                            && Modifier.isFinal(modifiers))
                    .as("%s must be public static final", field.getName())
                    .isTrue();
                assertThat(field.getType()).as(field.getName()).isEqualTo(String.class);
              });
      assertThat(declared(DocsLinks.class.getDeclaredMethods())).isEmpty();
      assertThat(DocsLinks.class.getDeclaredClasses()).isEmpty();
      assertThat(constructors)
          .singleElement()
          .satisfies(
              constructor -> {
                assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
                assertThat(constructor.getParameterCount()).isZero();
              });
    }

    @Test
    @DisplayName("Its source names the docs only in literals that are the values of its constants")
    void given_docsLinksSource_should_holdOnlyConstantValues()
        throws IOException, IllegalAccessException {
      // Arrange
      String source = Files.readString(repositoryRoot.resolve(SOURCE_FILE), StandardCharsets.UTF_8);
      Collection<String> values = constants().values();

      // Act
      List<String> unexpected = new ArrayList<>();
      Matcher domain = DOCS_DOMAIN.matcher(source);
      while (domain.find()) {
        Matcher literal = STRING_LITERAL.matcher(source);
        String found = null;
        while (literal.find()) {
          if (literal.start() < domain.start() && domain.end() < literal.end()) {
            found = literal.group(1);
            break;
          }
        }
        if (found == null || !values.contains(found)) {
          unexpected.add(
              lineOf(source, domain.start())
                  + ": "
                  + (found == null ? "outside a string literal" : found));
        }
      }

      // Assert
      assertThat(unexpected).as(SOURCE_FILE).isEmpty();
    }
  }

  @Nested
  @DisplayName("When application.properties links to the docs")
  class PropertiesComments {

    @Test
    @DisplayName("Every docs URL of its comments is a link of docs/links.json")
    void given_commentUrls_should_beLinksOfTheMap() throws IOException {
      // Arrange
      List<String> lines =
          Files.readAllLines(repositoryRoot.resolve(PROPERTIES_FILE), StandardCharsets.UTF_8);

      // Act
      List<String> found = new ArrayList<>();
      List<String> unknown = new ArrayList<>();
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i).stripLeading();
        if (!line.startsWith("#") && !line.startsWith("!")) {
          continue;
        }
        Matcher matcher = DOCS_URL.matcher(line);
        while (matcher.find()) {
          String url = stripTrailingPunctuation(matcher.group());
          String withScheme = matcher.group(1) == null ? "https://" + url : url;
          found.add(url);
          if (!urls.containsValue(withScheme)) {
            unknown.add((i + 1) + ": " + url);
          }
        }
      }

      // Assert
      assertThat(found).isNotEmpty();
      assertThat(unknown).as(PROPERTIES_FILE).isEmpty();
    }
  }
}
