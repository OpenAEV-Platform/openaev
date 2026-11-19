package io.openaev.config;

import static io.openaev.config.MinioConfig.IGNORED_MESSAGE;
import static io.openaev.config.MinioConfig.SHADOWED_MESSAGE;
import static io.openaev.config.MinioConfig.USED_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

@DisplayName("S3 storage configuration")
class MinioConfigTest {

  // The openaev.s3 names of the packaged application.properties (the test one shadows it on the
  // classpath, so it is read from the sources of the module)
  private static final Map<String, Object> PACKAGED =
      packagedProperties(Path.of("src/main/resources/application.properties"));

  // The packaged application.properties before the rename (minio.* section)
  private static final Map<String, Object> FORMER_PACKAGED =
      Map.of(
          "minio.endpoint", "localhost",
          "minio.port", "9000",
          "minio.bucket", "openaev",
          "minio.access-key", "<key>",
          "minio.access-secret", "<secret>");

  private static final String LOGGING_SYSTEM_PROPERTY =
      "org.springframework.boot.logging.LoggingSystem";

  private Logger logger;
  private ListAppender<ILoggingEvent> logs;
  private Level originalLevel;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(MinioConfig.class);
    // The test logging config caps io.openaev at ERROR once a Spring context has started in the
    // same JVM: force the level so the warnings are seen, and restore it afterwards.
    originalLevel = logger.getLevel();
    logger.setLevel(Level.WARN);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(originalLevel);
  }

  private static Map<String, Object> packagedProperties(Path file) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + file.toAbsolutePath(), e);
    }
    Map<String, Object> values = new HashMap<>();
    properties.stringPropertyNames().stream()
        .filter(name -> name.startsWith(MinioConfig.PREFIX + "."))
        .forEach(name -> values.put(name, properties.getProperty(name)));
    return values;
  }

  /**
   * Property sources in Spring order: command line arguments, environment variables, external file,
   * profile file, packaged file.
   */
  private static ConfigurableEnvironment environment(
      Map<String, Object> arguments,
      Map<String, Object> variables,
      Map<String, Object> externalFile,
      Map<String, Object> profileFile,
      Map<String, Object> packaged) {
    StandardEnvironment environment = isolatedEnvironment(variables);
    MutablePropertySources sources = environment.getPropertySources();
    sources.addFirst(new MapPropertySource("commandLineArgs", arguments));
    sources.addLast(
        new MapPropertySource(
            "Config resource 'file [config/application.properties]' via location"
                + " 'optional:file:./config/'",
            externalFile));
    sources.addLast(
        new MapPropertySource(
            "Config resource 'class path resource [application-prod.properties]' via location"
                + " 'optional:classpath:/'",
            profileFile));
    sources.addLast(
        new MapPropertySource(
            "Config resource 'class path resource [application.properties]' via location"
                + " 'optional:classpath:/'",
            packaged));
    // Attached like in the application: this source holds every key and must not decide
    ConfigurationPropertySources.attach(environment);
    return environment;
  }

  private static ConfigurableEnvironment environment(
      Map<String, Object> variables, Map<String, Object> externalFile) {
    return environment(Map.of(), variables, externalFile, Map.of(), PACKAGED);
  }

  /** An environment that ignores the variables and system properties of the test JVM. */
  private static StandardEnvironment isolatedEnvironment(Map<String, Object> variables) {
    StandardEnvironment environment = new StandardEnvironment();
    MutablePropertySources sources = environment.getPropertySources();
    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    sources.replace(
        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
    return environment;
  }

  private static RenamedConfigurationSection section(ConfigurableEnvironment environment) {
    return new RenamedConfigurationSection(
        environment, MinioConfig.PREFIX, MinioConfig.LEGACY_PREFIX);
  }

  /** Each warning as its level and the text written to the log. */
  private List<String> warnings() {
    return logs.list.stream().map(e -> e.getLevel() + " " + e.getFormattedMessage()).toList();
  }

  /** The former binding: {@code @ConfigurationProperties(prefix = "minio")} on these fields. */
  public static class FormerMinioProperties {
    private String endpoint;
    private String accessKey;
    private String accessSecret;
    private int port = 9000;
    private String bucket = "openaev";
    private boolean secure = false;

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public void setAccessSecret(String accessSecret) {
      this.accessSecret = accessSecret;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }

    public void setSecure(boolean secure) {
      this.secure = secure;
    }

    List<Object> values() {
      return Arrays.asList(endpoint, accessKey, accessSecret, port, bucket, secure);
    }
  }

  private static List<Object> values(MinioConfig config) {
    return Arrays.asList(
        config.getEndpoint(),
        config.getAccessKey(),
        config.getAccessSecret(),
        config.getPort(),
        config.getBucket(),
        config.isSecure());
  }

  @Nested
  @DisplayName("Same values as the former binding")
  class SameValuesAsBefore {

    @Test
    @DisplayName("given the packaged file should keep the values of the former minio section")
    void given_packagedFile_should_keepFormerValues() {
      // Arrange
      Map<String, Object> renamed = new HashMap<>();
      FORMER_PACKAGED.forEach(
          (name, value) ->
              renamed.put(
                  name.replaceFirst(MinioConfig.LEGACY_PREFIX + "\\.", MinioConfig.PREFIX + "."),
                  value));

      // Assert
      assertThat(PACKAGED).containsAllEntriesOf(renamed);
    }

    /** Binds the former names like before the rename, then the new code after it. */
    private void assertSameValues(
        String scenario,
        Map<String, Object> arguments,
        Map<String, Object> variables,
        Map<String, Object> externalFile,
        Map<String, Object> profileFile) {
      // Arrange
      ConfigurableEnvironment before =
          environment(arguments, variables, externalFile, profileFile, FORMER_PACKAGED);
      ConfigurableEnvironment after =
          environment(arguments, variables, externalFile, profileFile, PACKAGED);

      // Act
      FormerMinioProperties former =
          Binder.get(before)
              .bind(MinioConfig.LEGACY_PREFIX, Bindable.ofInstance(new FormerMinioProperties()))
              .orElseGet(FormerMinioProperties::new);
      MinioConfig config = new MinioConfig(after);

      // Assert
      assertThat(values(config)).as(scenario).isEqualTo(former.values());
    }

    @Test
    @DisplayName("given former names in every kind of source should bind the same values")
    void given_formerNamesInEverySource_should_bindSameValues() {
      Map<String, Object> none = Map.of();
      assertSameValues("packaged only", none, none, none, none);
      assertSameValues(
          "environment variables",
          none,
          Map.of(
              "MINIO_ENDPOINT", "minio",
              "MINIO_PORT", "19000",
              "MINIO_SECURE", "true",
              "MINIO_ACCESS-KEY", "legacy-access",
              "MINIO_ACCESSKEY", "other-spelling",
              "MINIO_ACCESS_SECRET", "legacy-secret",
              "MINIO_BUCKET", "files"),
          none,
          none);
      assertSameValues(
          "file keys in other spellings",
          none,
          none,
          Map.of("minio.accessKey", "camel", "minio.access_secret", "underscore"),
          none);
      Map<String, Object> placeholders = new LinkedHashMap<>();
      placeholders.put("minio.endpoint", "${S3_HOST}");
      placeholders.put("minio.port", "${S3_PORT:9100}");
      assertSameValues(
          "placeholders", none, Map.of("S3_HOST", "placeholder-host"), placeholders, none);
      assertSameValues("profile file", none, none, none, Map.of("minio.bucket", "profile-bucket"));
      assertSameValues(
          "command line over environment",
          Map.of("minio.port", "29000"),
          Map.of("MINIO_PORT", "19000", "MINIO_ENDPOINT", "minio"),
          none,
          none);
      assertSameValues("empty values", none, Map.of("MINIO_ENDPOINT", ""), none, none);
    }

    /** The former binding refused to start on an empty number or boolean. */
    private void assertFormerFailsAndNewUsesDefault(String variable) {
      // Arrange
      ConfigurableEnvironment before =
          environment(Map.of(), Map.of(variable, ""), Map.of(), Map.of(), FORMER_PACKAGED);
      ConfigurableEnvironment after =
          environment(Map.of(), Map.of(variable, ""), Map.of(), Map.of(), PACKAGED);

      // Act and assert
      assertThatThrownBy(
              () ->
                  Binder.get(before)
                      .bind(
                          MinioConfig.LEGACY_PREFIX,
                          Bindable.ofInstance(new FormerMinioProperties())))
          .as(variable)
          .isInstanceOf(BindException.class);
      MinioConfig config = new MinioConfig(after);
      assertThat(config.getPort()).as(variable).isEqualTo(9000);
      assertThat(config.isSecure()).as(variable).isFalse();
    }

    @Test
    @DisplayName("given an empty minio port should start on the default port")
    void given_emptyMinioPort_should_startOnDefaultPort() {
      assertFormerFailsAndNewUsesDefault("MINIO_PORT");
    }

    @Test
    @DisplayName("given an empty minio secure should start without TLS")
    void given_emptyMinioSecure_should_startWithoutTls() {
      assertFormerFailsAndNewUsesDefault("MINIO_SECURE");
    }
  }

  @Nested
  @DisplayName("Former minio names")
  class FormerNames {

    @Test
    @DisplayName("given minio environment variables should read them and report them")
    void given_minioVariables_should_readThemAndReportThem() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(
              Map.of(
                  "MINIO_ENDPOINT", "minio",
                  "MINIO_PORT", "19000",
                  "MINIO_SECURE", "true",
                  "MINIO_ACCESS_KEY", "legacy-access",
                  "MINIO_ACCESS_SECRET", "legacy-secret"),
              Map.of());

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getEndpoint()).isEqualTo("minio");
      assertThat(config.getPort()).isEqualTo(19000);
      assertThat(config.isSecure()).isTrue();
      assertThat(config.getAccessKey()).isEqualTo("legacy-access");
      assertThat(config.getAccessSecret()).isEqualTo("legacy-secret");
      assertThat(config.getBucket()).isEqualTo(PACKAGED.get("openaev.s3.bucket"));
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);
      assertThat(deprecations.used())
          .containsExactly(
              "minio.endpoint",
              "minio.port",
              "minio.access-key",
              "minio.access-secret",
              "minio.secure");
      assertThat(deprecations.ignored()).isEmpty();
      assertThat(deprecations.shadowed()).isEmpty();
    }

    @Test
    @DisplayName("given every environment spelling should read the same key")
    void given_everyEnvironmentSpelling_should_readTheSameKey() {
      for (String variable : List.of("MINIO_ACCESS_KEY", "MINIO_ACCESS-KEY", "MINIO_ACCESSKEY")) {
        // Act
        MinioConfig config = new MinioConfig(environment(Map.of(variable, "legacy"), Map.of()));

        // Assert
        assertThat(config.getAccessKey()).as(variable).isEqualTo("legacy");
      }
    }

    @Test
    @DisplayName("given every file key spelling should read the same key")
    void given_everyFileKeySpelling_should_readTheSameKey() {
      for (String key : List.of("minio.access-key", "minio.accessKey", "minio.access_key")) {
        // Act
        MinioConfig config = new MinioConfig(environment(Map.of(), Map.of(key, "legacy")));

        // Assert
        assertThat(config.getAccessKey()).as(key).isEqualTo("legacy");
      }
    }

    @Test
    @DisplayName("given a minio file key should read it like before and report it")
    void given_minioFileKey_should_readItLikeBeforeAndReportIt() {
      // Arrange
      ConfigurableEnvironment environment = environment(Map.of(), Map.of("minio.bucket", "files"));

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getBucket()).isEqualTo("files");
      assertThat(section(environment).deprecations(MinioConfig.KEYS).used())
          .containsExactly("minio.bucket");
    }

    @Test
    @DisplayName("given empty minio values should count them as set")
    void given_emptyMinioValues_should_countThemAsSet() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(Map.of("MINIO_ENDPOINT", "", "MINIO_PORT", ""), Map.of());

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert: an empty string stays empty, an empty number falls back to the default
      assertThat(section(environment).resolve("endpoint")).isEqualTo("minio.endpoint");
      assertThat(section(environment).resolve("port")).isEqualTo("minio.port");
      assertThat(config.getEndpoint()).isEmpty();
      assertThat(config.getPort()).isEqualTo(9000);
    }
  }

  @Nested
  @DisplayName("Priority between names")
  class Priority {

    @Test
    @DisplayName("given both names in a same source should prefer openaev.s3")
    void given_bothNamesInSameSource_should_preferNewName() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(
              Map.of("OPENAEV_S3_ENDPOINT", "new-host", "MINIO_ENDPOINT", "old-host"), Map.of());

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getEndpoint()).isEqualTo("new-host");
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);
      assertThat(deprecations.used()).isEmpty();
      assertThat(deprecations.ignored()).containsExactly("minio.endpoint");
    }

    @Test
    @DisplayName("given an empty openaev.s3 name should count it as set")
    void given_emptyNewName_should_countItAsSet() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(Map.of("OPENAEV_S3_ENDPOINT", ""), Map.of("minio.endpoint", "old-host"));

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(section(environment).resolve("endpoint")).isEqualTo("openaev.s3.endpoint");
      assertThat(config.getEndpoint()).isEmpty();
      assertThat(section(environment).deprecations(MinioConfig.KEYS).ignored())
          .containsExactly("minio.endpoint");
    }

    @Test
    @DisplayName("given minio in the environment and openaev.s3 in a file should keep minio")
    void given_minioVariableAndNewFileKey_should_keepMinio() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(Map.of("MINIO_PORT", "19000"), Map.of("openaev.s3.port", "9001"));

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getPort()).isEqualTo(19000);
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);
      assertThat(deprecations.used()).containsExactly("minio.port");
      assertThat(deprecations.shadowed()).containsExactly("openaev.s3.port");
    }

    @Test
    @DisplayName("given a new name the packaged file does not set should report it as hidden")
    void given_newNameAbsentFromPackagedFile_should_reportItAsHidden() {
      // Arrange: the packaged file does not set secure
      ConfigurableEnvironment environment =
          environment(Map.of("MINIO_SECURE", "true"), Map.of("openaev.s3.secure", "false"));

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(PACKAGED).doesNotContainKey("openaev.s3.secure");
      assertThat(config.isSecure()).isTrue();
      assertThat(section(environment).deprecations(MinioConfig.KEYS).shadowed())
          .containsExactly("openaev.s3.secure");
    }

    @Test
    @DisplayName("given a copy of the packaged file next to the jar should not report its names")
    void given_copyOfPackagedFile_should_notReportItsNames() {
      // Arrange: the release archive ships application.properties next to the jar
      ConfigurableEnvironment environment = environment(Map.of("MINIO_PORT", "19000"), PACKAGED);

      // Act
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);

      // Assert
      assertThat(deprecations.used()).containsExactly("minio.port");
      assertThat(deprecations.shadowed()).isEmpty();
    }

    @Test
    @DisplayName("given openaev.s3 in the environment and minio in a file should keep openaev.s3")
    void given_newVariableAndMinioFileKey_should_keepNewName() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(Map.of("OPENAEV_S3_PORT", "19000"), Map.of("minio.port", "9001"));

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getPort()).isEqualTo(19000);
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);
      assertThat(deprecations.used()).isEmpty();
      assertThat(deprecations.ignored()).containsExactly("minio.port");
    }

    @Test
    @DisplayName("given openaev.s3 environment variables with underscores should read them")
    void given_newVariablesWithUnderscores_should_readThem() {
      // Act
      MinioConfig config =
          new MinioConfig(
              environment(
                  Map.of(
                      "OPENAEV_S3_ACCESS_KEY", "new-access",
                      "OPENAEV_S3_ACCESS_SECRET", "new-secret"),
                  Map.of()));

      // Assert
      assertThat(config.getAccessKey()).isEqualTo("new-access");
      assertThat(config.getAccessSecret()).isEqualTo("new-secret");
    }

    @Test
    @DisplayName("given no minio name should read openaev.s3 and report nothing")
    void given_noMinioName_should_readNewNamesAndReportNothing() {
      // Arrange
      ConfigurableEnvironment environment =
          environment(Map.of("OPENAEV_S3_ENDPOINT", "new-host"), Map.of());

      // Act
      MinioConfig config = new MinioConfig(environment);

      // Assert
      assertThat(config.getEndpoint()).isEqualTo("new-host");
      assertThat(config.getAccessKey()).isEqualTo(PACKAGED.get("openaev.s3.access-key"));
      assertThat(config.getPort()).isEqualTo(9000);
      assertThat(config.isSecure()).isFalse();
      RenamedConfigurationSection.Deprecations deprecations =
          section(environment).deprecations(MinioConfig.KEYS);
      assertThat(deprecations.used()).isEmpty();
      assertThat(deprecations.ignored()).isEmpty();
      assertThat(deprecations.shadowed()).isEmpty();
    }

    @Test
    @DisplayName("given no source setting a key should apply the defaults")
    void given_noSource_should_applyDefaults() {
      // Act
      MinioConfig config =
          new MinioConfig(environment(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));

      // Assert
      assertThat(config.getEndpoint()).isNull();
      assertThat(config.getAccessKey()).isNull();
      assertThat(config.getAccessSecret()).isNull();
      assertThat(config.getPort()).isEqualTo(9000);
      assertThat(config.getBucket()).isEqualTo("openaev");
      assertThat(config.isSecure()).isFalse();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class EmptyConfiguration {}

  @Nested
  @DisplayName("Spring Boot configuration loading")
  class SpringBootLoading {

    @Test
    @DisplayName(
        "given a minio argument should read it and not report the application.properties names")
    void given_minioArgument_should_readItAndNotReportApplicationPropertiesNames() {
      // Arrange: Spring Boot loads the test application.properties (it shadows the packaged one on
      // the test classpath), which sets every openaev.s3 name. The variables of the test JVM are
      // left out, and Spring Boot logging stays off: its levels would outlive the context.
      String previousLoggingSystem = System.setProperty(LOGGING_SYSTEM_PROPERTY, "none");
      try (ConfigurableApplicationContext context =
          new SpringApplicationBuilder(EmptyConfiguration.class)
              .environment(isolatedEnvironment(Map.of()))
              .web(WebApplicationType.NONE)
              .run("--spring.config.location=classpath:/", "--minio.endpoint=argument-host")) {
        ConfigurableEnvironment environment = context.getEnvironment();

        // Act
        MinioConfig config = new MinioConfig(environment);

        // Assert: the port and the key only come from the test application.properties
        assertThat(
                StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                    .map(s -> s.getName()))
            .anyMatch(name -> name.contains("class path resource [application.properties]"));
        assertThat(config.getEndpoint()).isEqualTo("argument-host");
        assertThat(config.getPort()).isEqualTo(11000);
        assertThat(config.getAccessKey()).isEqualTo("minioadmin");
        RenamedConfigurationSection.Deprecations deprecations =
            section(environment).deprecations(MinioConfig.KEYS);
        assertThat(deprecations.used()).containsExactly("minio.endpoint");
        assertThat(deprecations.shadowed()).isEmpty();
      } finally {
        if (previousLoggingSystem == null) {
          System.clearProperty(LOGGING_SYSTEM_PROPERTY);
        } else {
          System.setProperty(LOGGING_SYSTEM_PROPERTY, previousLoggingSystem);
        }
      }
    }
  }

  @Nested
  @DisplayName("Startup warnings")
  class Warnings {

    @Test
    @DisplayName("given former names in use or ignored should log each list, never values")
    void given_formerNamesUsedOrIgnored_should_logEachListWithoutValues() {
      // Act
      new MinioConfig(
          environment(
              Map.of(
                  "MINIO_ACCESS_SECRET", "super-secret-value",
                  "OPENAEV_S3_ENDPOINT", "new-host",
                  "MINIO_ENDPOINT", "old-host"),
              Map.of()));

      // Assert
      assertThat(warnings())
          .containsExactly(
              "WARN " + USED_MESSAGE.replace("{}", "[minio.access-secret]"),
              "WARN " + IGNORED_MESSAGE.replace("{}", "[minio.endpoint]"));
      assertThat(warnings().get(0)).endsWith(": [minio.access-secret]");
      assertThat(warnings().get(1)).endsWith(": [minio.endpoint]");
      assertThat(String.join(" ", warnings()))
          .doesNotContain("super-secret-value")
          .doesNotContain("old-host")
          .doesNotContain("new-host");
    }

    @Test
    @DisplayName("given a new name hidden by a former one should log it, never values")
    void given_newNameHiddenByFormerOne_should_logItWithoutValues() {
      // Act
      new MinioConfig(
          environment(Map.of("MINIO_PORT", "19000"), Map.of("openaev.s3.port", "9001")));

      // Assert
      assertThat(warnings())
          .containsExactly(
              "WARN " + USED_MESSAGE.replace("{}", "[minio.port]"),
              "WARN " + SHADOWED_MESSAGE.replace("{}", "[openaev.s3.port]"));
      assertThat(warnings().get(0)).endsWith(": [minio.port]");
      assertThat(warnings().get(1)).endsWith(": [openaev.s3.port]");
      assertThat(String.join(" ", warnings())).doesNotContain("19000").doesNotContain("9001");
    }

    @Test
    @DisplayName("given no minio name should log nothing")
    void given_noMinioName_should_logNothing() {
      // Arrange: the capture works
      new MinioConfig(environment(Map.of("MINIO_ENDPOINT", "old-host"), Map.of()));
      assertThat(logs.list).hasSize(1);
      logs.list.clear();

      // Act
      new MinioConfig(environment(Map.of("OPENAEV_S3_ENDPOINT", "new-host"), Map.of()));

      // Assert
      assertThat(logs.list).isEmpty();
    }
  }
}
