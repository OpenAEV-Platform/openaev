package io.openaev.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

  /** A docs URL, until a character that cannot be part of it. */
  private static final Pattern DOCS_URL =
      Pattern.compile("https?://docs\\.openaev\\.io[^\\s\"'<>\\[\\]{}|\\\\^`]*");

  /** Sentence punctuation that follows a URL in prose rather than belonging to it. */
  private static final String TRAILING_PUNCTUATION = ".,;:!?)";

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
   * The full URL of each key of a links map: {@code base} followed by the key's path. Other
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

  private static Map<String, String> constants() throws IllegalAccessException {
    Map<String, String> constants = new TreeMap<>();
    for (Field field : DocsLinks.class.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (Modifier.isPublic(modifiers)) {
        assertThat(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers))
            .as("%s must be public static final", field.getName())
            .isTrue();
        assertThat(field.getType()).as(field.getName()).isEqualTo(String.class);
        constants.put(field.getName(), (String) field.get(null));
      }
    }
    return constants;
  }

  private static String stripTrailingPunctuation(String url) {
    int end = url.length();
    while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
      end--;
    }
    return url.substring(0, end);
  }

  /** The docs URLs of the comment lines of a properties file, as {@code line: url}. */
  private static List<String> commentUrls(List<String> lines) {
    List<String> found = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).stripLeading();
      if (!line.startsWith("#") && !line.startsWith("!")) {
        continue;
      }
      Matcher matcher = DOCS_URL.matcher(line);
      while (matcher.find()) {
        found.add((i + 1) + ": " + stripTrailingPunctuation(matcher.group()));
      }
    }
    return found;
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
    @DisplayName("Every public constant is a key of the map")
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
  @DisplayName("When application.properties links to the docs")
  class PropertiesComments {

    @Test
    @DisplayName("Every docs URL of its comments is a link of docs/links.json")
    void given_commentUrls_should_beLinksOfTheMap() throws IOException {
      // Arrange
      List<String> lines =
          Files.readAllLines(repositoryRoot.resolve(PROPERTIES_FILE), StandardCharsets.UTF_8);

      // Act
      List<String> found = commentUrls(lines);

      // Assert
      assertThat(found).isNotEmpty();
      assertThat(found)
          .allSatisfy(
              lineAndUrl ->
                  assertThat(urls.values())
                      .as("%s:%s", PROPERTIES_FILE, lineAndUrl)
                      .contains(lineAndUrl.substring(lineAndUrl.indexOf(": ") + 2)));
    }

    @Test
    @DisplayName("A URL followed by sentence punctuation is read without it")
    void given_trailingPunctuation_should_stripIt() {
      // Arrange
      List<String> lines =
          List.of(
              "### see also: https://docs.openaev.io/latest/a/#b.",
              "# (https://docs.openaev.io/latest/c/);",
              "key=https://docs.openaev.io/latest/not-a-comment/");

      // Act
      List<String> found = commentUrls(lines);

      // Assert
      assertThat(found)
          .containsExactly(
              "1: https://docs.openaev.io/latest/a/#b", "2: https://docs.openaev.io/latest/c/");
    }
  }

  @Nested
  @DisplayName("When reading a links map")
  class ReadingLinksMap {

    @Test
    @DisplayName("Other top-level members, such as retired, are ignored")
    void given_retiredMember_should_beIgnored() throws IOException {
      // Arrange
      String json =
          """
          {"base": "https://docs.openaev.io/latest/",
           "links": {"home": "", "teams": "usage/build/people/#teams"},
           "retired": ["old/page/"]}
          """;

      // Act
      Map<String, String> read = readUrls(json);

      // Assert
      assertThat(read)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "home", "https://docs.openaev.io/latest/",
                  "teams", "https://docs.openaev.io/latest/usage/build/people/#teams"));
    }
  }
}
