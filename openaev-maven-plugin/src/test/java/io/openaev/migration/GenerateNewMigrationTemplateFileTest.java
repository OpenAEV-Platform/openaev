package io.openaev.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.*;

public class GenerateNewMigrationTemplateFileTest {
  private File tempDir;
  private final String reason = "migration";
  private final String subLocation = "src/main/java/io/openaev/migration";
  private final String tempDirPrefix = "io.openaev-maven-plugin-tests";
  private final ClassContentsGenerator classContentsGenerator = new ClassContentsGenerator();
  private final ClassNameGenerator classNameGenerator = new ClassNameGenerator();

  @Nested
  @DisplayName("When base directory is not valid")
  class BaseDirectoryNotValid {
    @Test
    @DisplayName("It throws an exception")
    void given_invalidBaseDir_then_throwException() throws MojoExecutionException {
      GenerateNewMigrationTemplateFile mojo =
          new GenerateNewMigrationTemplateFile(new File("does_not_exist"), reason);

      assertThatThrownBy(() -> mojo.execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessageContaining("This action cannot execute at this location");
    }
  }

  @Nested
  @DisplayName("When base directory is valid")
  class BaseDirectoryValid {
    @BeforeEach
    void before() throws IOException {
      tempDir = Files.createTempDirectory(tempDirPrefix).toFile();
      new File(tempDir, "src/main/java/io/openaev/migration").mkdirs();
    }

    @AfterEach
    void after() throws IOException {
      FileUtils.deleteDirectory(tempDir);
    }

    private Optional<File> getTempClassFile() {
      String dirPath = "%s/%s".formatted(tempDir.getAbsolutePath(), subLocation);
      return Arrays.stream(Objects.requireNonNull(new File(dirPath).listFiles())).findFirst();
    }

    @Test
    @DisplayName("It creates a class file")
    void given_validDirectoryPath_then_createClassFile() throws MojoExecutionException {
      GenerateNewMigrationTemplateFile mojo = new GenerateNewMigrationTemplateFile(tempDir, reason);

      mojo.execute();

      assertThat(getTempClassFile()).isNotEmpty();
    }

    @Test
    @DisplayName("It generates valid contents")
    void given_validDirectoryPath_then_createCorrectContents()
        throws MojoExecutionException, IOException {
      GenerateNewMigrationTemplateFile mojo = new GenerateNewMigrationTemplateFile(tempDir, reason);

      mojo.execute();

      File generated = getTempClassFile().get();
      String className = generated.getName().replace(".java", "");

      String actualContents = Files.readString(Paths.get(generated.getAbsolutePath()));
      assertThat(actualContents).isEqualTo(classContentsGenerator.generate(className));
    }
  }
}
