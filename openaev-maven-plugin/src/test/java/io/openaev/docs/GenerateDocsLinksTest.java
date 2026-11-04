package io.openaev.docs;

import static io.openaev.docs.DocsLinksGenerator.BASE;
import static io.openaev.docs.DocsLinksGenerator.MAX_KEY_LENGTH;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_BASE;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_CHARACTERS;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_FILE_NOT_FOUND;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_INVALID_JSON;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_KEY;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_KEY_LENGTH;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_LINKS;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_NOT_STRING;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_OTHER_VERSION;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_PAGE;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_RELATIVE;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_UNKNOWN_FIELD;
import static io.openaev.docs.DocsLinksGenerator.MESSAGE_UNKNOWN_URL;
import static io.openaev.docs.GenerateDocsLinks.MESSAGE_OUT_OF_DATE;
import static io.openaev.docs.GenerateDocsLinks.MESSAGE_UNKNOWN_URLS;
import static io.openaev.docs.GenerateDocsLinks.UPDATE_COMMAND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.googlejavaformat.java.Formatter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.*;

public class GenerateDocsLinksTest {
  private static final String GENERATED_FILE =
      "openaev-api/src/main/java/io/openaev/docs/DocsLinks.java";
  private static final String VALID_LINKS =
      """
      {
        "base": "%s",
        "links": {
          "teams": "usage/build/people/#teams",
          "home": "",
          "configuration_mail_services": "reference/deployment/configuration/#mail-services"
        }
      }
      """
          .formatted(BASE);

  private File tempDir;
  private File linksFile;
  private File sourceDir;
  private File propertiesFile;

  @BeforeEach
  void before() throws IOException {
    tempDir = Files.createTempDirectory("io.openaev-maven-plugin-docs-links").toFile();
    linksFile = new File(tempDir, "docs/links.json");
    linksFile.getParentFile().mkdirs();
    sourceDir = new File(tempDir, "openaev-api/src/main/java");
    propertiesFile = new File(tempDir, "application.properties");
  }

  @AfterEach
  void after() throws IOException {
    FileUtils.deleteDirectory(tempDir);
  }

  private GenerateDocsLinks mojo(File links, Map<String, String> env, List<File> linkedFiles) {
    GenerateDocsLinks mojo = new GenerateDocsLinks(links, sourceDir, tempDir, linkedFiles, env);
    mojo.setLog(new SystemStreamLog());
    return mojo;
  }

  private GenerateDocsLinks localMojo() {
    return mojo(linksFile, Map.of(), List.of());
  }

  private GenerateDocsLinks ciMojo() {
    return mojo(linksFile, Map.of("CI", "true"), List.of());
  }

  private GenerateDocsLinks linkedFilesMojo() {
    return mojo(linksFile, Map.of(), List.of(propertiesFile));
  }

  private void writeLinks(String json) throws IOException {
    Files.writeString(linksFile.toPath(), json, StandardCharsets.UTF_8);
  }

  private void writeLinksEntries(String entries) throws IOException {
    writeLinks("{\"base\": \"" + BASE + "\", \"links\": {" + entries + "}}");
  }

  private void writeProperties(String contents) throws IOException {
    Files.writeString(propertiesFile.toPath(), contents, StandardCharsets.UTF_8);
  }

  private Path generatedFile() {
    return sourceDir.toPath().resolve("io/openaev/docs/DocsLinks.java");
  }

  @Nested
  @DisplayName("When the links file is valid")
  class ValidLinks {
    @Test
    @DisplayName("It writes one sorted constant per key, wrapped as google-java-format does")
    void given_validLinks_should_generateSortedConstants() throws Exception {
      // Arrange
      writeLinks(VALID_LINKS);

      // Act
      localMojo().execute();

      // Assert
      assertThat(Files.readString(generatedFile()))
          .isEqualTo(
              """
              // Generated from docs/links.json by the docs-links goal of openaev-maven-plugin. Do not edit:
              // change docs/links.json and build openaev-api with Maven, which rewrites this file.
              package io.openaev.docs;

              /** Links to the OpenAEV documentation, one constant per key of docs/links.json. */
              public final class DocsLinks {

                /** Key {@code configuration_mail_services}. */
                public static final String CONFIGURATION_MAIL_SERVICES =
                    "https://docs.openaev.io/latest/reference/deployment/configuration/#mail-services";

                /** Key {@code home}. */
                public static final String HOME = "https://docs.openaev.io/latest/";

                /** Key {@code teams}. */
                public static final String TEAMS = "https://docs.openaev.io/latest/usage/build/people/#teams";

                private DocsLinks() {}
              }
              """);
    }

    @Test
    @DisplayName("It writes the same header whatever the path of the map")
    void given_mapElsewhere_should_writeSameHeader() throws Exception {
      // Arrange
      writeLinks(VALID_LINKS);
      localMojo().execute();
      String fromDefaultPath = Files.readString(generatedFile());
      Files.delete(generatedFile());
      File elsewhere = new File(tempDir, "somewhere/else/map.json");
      elsewhere.getParentFile().mkdirs();
      Files.writeString(elsewhere.toPath(), VALID_LINKS, StandardCharsets.UTF_8);

      // Act
      mojo(elsewhere, Map.of(), List.of()).execute();

      // Assert
      assertThat(Files.readString(generatedFile())).isEqualTo(fromDefaultPath);
    }

    @Test
    @DisplayName("It does not rewrite an up-to-date file, even in CI")
    void given_upToDateFile_should_keepItUntouched() throws Exception {
      // Arrange
      writeLinks(VALID_LINKS);
      localMojo().execute();
      FileTime old = FileTime.fromMillis(1_000_000_000_000L);
      Files.setLastModifiedTime(generatedFile(), old);

      // Act
      localMojo().execute();
      ciMojo().execute();

      // Assert
      assertThat(Files.getLastModifiedTime(generatedFile())).isEqualTo(old);
    }

    @Test
    @DisplayName("It rewrites a stale file in place outside CI")
    void given_staleFileLocally_should_rewriteIt() throws Exception {
      // Arrange
      writeLinks(VALID_LINKS);
      localMojo().execute();
      writeLinks(VALID_LINKS.replace("#teams", "#groups"));

      // Act
      mojo(linksFile, Map.of("CI", "false"), List.of()).execute();

      // Assert
      assertThat(Files.readString(generatedFile())).contains("people/#groups\";");
    }

    @Test
    @DisplayName("It fails on a stale file in CI, with the command that updates it")
    void given_staleFileInCi_should_failWithoutRewriting() throws Exception {
      // Arrange
      writeLinks(VALID_LINKS);
      localMojo().execute();
      String before = Files.readString(generatedFile());
      writeLinks(VALID_LINKS.replace("#teams", "#groups"));

      // Act / Assert
      assertThatThrownBy(() -> ciMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessage(GENERATED_FILE + MESSAGE_OUT_OF_DATE)
          .hasMessageContaining(UPDATE_COMMAND);
      assertThat(Files.readString(generatedFile())).isEqualTo(before);
    }

    @Test
    @DisplayName("It fails on a missing file in CI")
    void given_missingFileInCi_should_fail() throws IOException {
      // Arrange
      writeLinks(VALID_LINKS);

      // Act / Assert
      assertThatThrownBy(() -> ciMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessage(GENERATED_FILE + MESSAGE_OUT_OF_DATE);
      assertThat(generatedFile()).doesNotExist();
    }
  }

  @Nested
  @DisplayName("When formatting the generated class")
  class Formatting {
    @Test
    @DisplayName("It writes exactly what google-java-format (spotless) would")
    void given_keysOfEveryLength_should_matchGoogleJavaFormat() throws Exception {
      // Arrange
      String longestKey = "kk" + "_x".repeat((MAX_KEY_LENGTH - 2) / 2);
      SortedMap<String, String> urls = new TreeMap<>();
      urls.put("a", BASE);
      urls.put("teams", BASE + "usage/build/people/#teams");
      // "  public static final String FITS = " is 36 columns, the literal 64 or 65 with ";".
      urls.put("fits", BASE + "x".repeat(29) + "/");
      urls.put("wraps", BASE + "x".repeat(29) + "/");
      urls.put("long_url", BASE + "a/".repeat(60) + "#anchor");
      urls.put(longestKey, BASE + "page/#anchor");

      // Act
      String generated = new DocsLinksGenerator().generate(urls, "io.openaev.docs", "DocsLinks");

      // Assert
      assertThat(longestKey).hasSize(MAX_KEY_LENGTH);
      assertThat(new Formatter().formatSource(generated)).isEqualTo(generated);
    }

    @Test
    @DisplayName("It escapes what a Java string literal cannot hold, as a backstop")
    void given_specialCharacters_should_escapeThem() {
      // Arrange
      String value = "a\"b\\c\nd\re\tf\u0001gé";

      // Act
      String escaped = DocsLinksGenerator.javaString(value);

      // Assert
      assertThat(escaped).isEqualTo("a\\\"b\\\\c\\nd\\re\\tf\\001g\\u00e9");
    }
  }

  @Nested
  @DisplayName("When files of the build hold docs URLs")
  class LinkedFiles {
    @Test
    @DisplayName("It accepts URLs that are links of the map, followed or not by punctuation")
    void given_knownUrls_should_succeed() throws IOException {
      // Arrange
      writeLinks(VALID_LINKS);
      writeProperties(
          "### see also: "
              + BASE
              + "usage/build/people/#teams\n"
              + "# (see "
              + BASE
              + "usage/build/people/#teams).\n"
              + "# "
              + BASE
              + ", then "
              + BASE
              + "reference/deployment/configuration/#mail-services;\n");

      // Act / Assert
      assertThatCode(() -> linkedFilesMojo().execute()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("It fails on a URL that is not a link of the map, naming its line")
    void given_unknownUrl_should_fail() throws IOException {
      // Arrange
      writeLinks(VALID_LINKS);
      String unknown = BASE + "reference/deployment/configuration/#imap";
      writeProperties("a=b\n### see also: " + unknown + ".\n");

      // Act / Assert
      assertThatThrownBy(() -> linkedFilesMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining(MESSAGE_UNKNOWN_URLS)
          .hasMessageContaining(propertiesFile + ":2: " + unknown + " " + MESSAGE_UNKNOWN_URL);
    }

    @Test
    @DisplayName("It fails on a URL of another docs version or of the bare site")
    void given_urlOutsideBase_should_fail() throws IOException {
      // Arrange
      writeLinks(VALID_LINKS);
      String versioned = "https://docs.openaev.io/1.18/usage/build/people/";
      String root = "https://docs.openaev.io/";
      writeProperties("# " + versioned + "\n# " + root + "\n");

      // Act / Assert
      assertThatThrownBy(() -> linkedFilesMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining(":1: " + versioned + " " + MESSAGE_OTHER_VERSION)
          .hasMessageContaining(":2: " + root + " " + MESSAGE_OTHER_VERSION);
    }
  }

  @Nested
  @DisplayName("When the links file is invalid")
  class InvalidLinks {
    @Test
    @DisplayName("It fails when the file is missing")
    void given_missingFile_should_fail() {
      // Act / Assert
      assertThatThrownBy(() -> localMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining(MESSAGE_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("It fails on malformed JSON")
    void given_malformedJson_should_fail() throws IOException {
      // Arrange
      writeLinksEntries("\"home\": ");

      // Act / Assert
      assertThatThrownBy(() -> localMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining(MESSAGE_INVALID_JSON)
          .hasMessageContaining("line 1");
    }

    @Test
    @DisplayName("It fails on a duplicate key")
    void given_duplicateKey_should_fail() throws IOException {
      // Arrange
      writeLinksEntries("\"home\": \"\", \"home\": \"other/\"");

      // Act / Assert
      assertThatThrownBy(() -> localMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining("Duplicate field 'home'");
    }

    @Test
    @DisplayName("It fails on another base, missing links or an unknown field")
    void given_badShape_should_fail() throws IOException {
      // Arrange
      writeLinks("{\"base\": \"https://docs.openaev.io/1.18/\", \"link\": {}}");

      // Act / Assert
      assertThatThrownBy(() -> localMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining(MESSAGE_UNKNOWN_FIELD + "\"link\"")
          .hasMessageContaining(MESSAGE_BASE)
          .hasMessageContaining(MESSAGE_LINKS);
    }

    @Test
    @DisplayName("It reports every invalid entry and writes nothing")
    void given_badEntries_should_reportAllAndNotGenerate() throws IOException {
      // Arrange
      String tooLong = "k" + "_x".repeat(MAX_KEY_LENGTH / 2);
      writeLinksEntries(
          "\"Home\": \"\", \"user-guide\": \"a/\", \"number\": 3, \"page\": \"a/b#x\","
              + " \"spaced\": \"a b/\", \"newline\": \"a\\n/\", \""
              + tooLong
              + "\": \"\"");

      // Act / Assert
      assertThatThrownBy(() -> localMojo().execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessageContaining("key \"Home\" " + MESSAGE_KEY)
          .hasMessageContaining("key \"user-guide\" " + MESSAGE_KEY)
          .hasMessageContaining("key \"number\" " + MESSAGE_NOT_STRING)
          .hasMessageContaining("key \"page\": \"a/b#x\" " + MESSAGE_PAGE)
          .hasMessageContaining("key \"spaced\": \"a b/\" " + MESSAGE_CHARACTERS)
          .hasMessageContaining("key \"newline\": \"a\\n/\" " + MESSAGE_CHARACTERS)
          .hasMessageContaining("key \"" + tooLong + "\" " + MESSAGE_KEY_LENGTH);
      assertThat(generatedFile()).doesNotExist();
    }
  }

  @Nested
  @DisplayName("When checking keys and paths (same rules as docs/hooks/checks.py)")
  class Rules {
    @Test
    @DisplayName("It accepts snake_case keys only")
    void given_keys_should_matchSnakeCase() {
      // Arrange
      List<String> valid = List.of("home", "a", "configuration_imap", "s3_bucket", "v2");
      List<String> invalid = List.of("Home", "1st", "_a", "a_", "a__b", "a-b", "a.b", "");

      // Act / Assert
      assertThat(valid).allMatch(key -> DocsLinksGenerator.KEY_PATTERN.matcher(key).matches());
      assertThat(invalid).noneMatch(key -> DocsLinksGenerator.KEY_PATTERN.matcher(key).matches());
    }

    @Test
    @DisplayName("It accepts relative page paths, with or without an anchor")
    void given_validPaths_should_beAccepted() {
      // Arrange
      List<String> paths =
          List.of("", "#anchor", "a/", "a/b/", "a/b/#anchor", "a/b/#x?y", "a%20b/", "a/~b_c-d.e/");

      // Act / Assert
      assertThat(paths).allMatch(path -> DocsLinksGenerator.pathProblem(path) == null);
    }

    @Test
    @DisplayName("It rejects absolute paths, pages without a final slash and non-URL characters")
    void given_invalidPaths_should_beRejected() {
      // Act / Assert
      assertThat(DocsLinksGenerator.pathProblem("https://elsewhere.io/a/"))
          .isEqualTo(MESSAGE_RELATIVE);
      assertThat(DocsLinksGenerator.pathProblem("mailto:someone@example.io"))
          .isEqualTo(MESSAGE_RELATIVE);
      assertThat(DocsLinksGenerator.pathProblem("/latest/a/")).isEqualTo(MESSAGE_RELATIVE);
      assertThat(DocsLinksGenerator.pathProblem("a/b")).isEqualTo(MESSAGE_PAGE);
      assertThat(DocsLinksGenerator.pathProblem("a/b.md#x")).isEqualTo(MESSAGE_PAGE);
      assertThat(DocsLinksGenerator.pathProblem("a/?x=1")).isEqualTo(MESSAGE_PAGE);
      assertThat(
              List.of(
                  "a b/",
                  "a\t/",
                  "a\n/",
                  "a\u0001/",
                  "é/",
                  "a\"/",
                  "a</",
                  "a\\/",
                  "a/%2",
                  "a/%zz/"))
          .allMatch(path -> MESSAGE_CHARACTERS.equals(DocsLinksGenerator.pathProblem(path)));
    }

    @Test
    @DisplayName("It strips the sentence punctuation that follows a URL")
    void given_trailingPunctuation_should_stripIt() {
      // Act / Assert
      assertThat(DocsLinksGenerator.stripTrailingPunctuation(BASE + "a/#b)."))
          .isEqualTo(BASE + "a/#b");
      assertThat(DocsLinksGenerator.stripTrailingPunctuation(BASE + "a/;")).isEqualTo(BASE + "a/");
      assertThat(DocsLinksGenerator.stripTrailingPunctuation(BASE + "a/")).isEqualTo(BASE + "a/");
    }
  }
}
