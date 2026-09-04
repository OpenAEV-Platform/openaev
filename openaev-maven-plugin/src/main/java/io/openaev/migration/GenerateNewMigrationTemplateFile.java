package io.openaev.migration;

import io.openaev.fs.ClassFileWriter;
import java.io.File;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Generate a new templated flyway migration file */
@Mojo(name = "migration", defaultPhase = LifecyclePhase.NONE)
public class GenerateNewMigrationTemplateFile extends AbstractMojo {
  @Parameter(
      property = "basedir",
      name = "basedir",
      required = true,
      defaultValue = "${project.basedir}")
  private File basedir;

  @Parameter(property = "reason", name = "reason", required = true, defaultValue = "migration")
  private String reason;

  private final ClassNameGenerator classNameGenerator;
  private final ClassContentsGenerator classContentsGenerator;
  private final ClassFileWriter classFileWriter;

  private final File defaultBaseDir;

  public GenerateNewMigrationTemplateFile() {
    this(
        new ClassNameGenerator(),
        new ClassFileWriter(),
        new ClassContentsGenerator(),
        new File("."),
        "migration");
  }

  public GenerateNewMigrationTemplateFile(File baseDir, String reason) {
    this(
        new ClassNameGenerator(),
        new ClassFileWriter(),
        new ClassContentsGenerator(),
        baseDir,
        reason);
  }

  public GenerateNewMigrationTemplateFile(
      ClassNameGenerator classNameGenerator,
      ClassFileWriter classFileWriter,
      ClassContentsGenerator classContentsGenerator,
      File baseDir,
      String reason) {
    this.classFileWriter = classFileWriter;
    this.classContentsGenerator = classContentsGenerator;
    this.classNameGenerator = classNameGenerator;
    this.defaultBaseDir = baseDir;
    this.defaultReason = reason;
  }

  private final String defaultReason;
  private final String subLocation = "src/main/java/io/openaev/migration";

  private String getClassName() {
    return StringUtils.isBlank(reason) ? defaultReason : reason;
  }

  private File getFinalBaseDir() {
    return basedir == null ? defaultBaseDir : basedir;
  }

  private boolean isInCorrectLocation(File location) {
    return location.exists() && new File(getFullDirectoryPath(location)).exists();
  }

  private String getFullDirectoryPath(File baseDir) {
    return new File(baseDir, subLocation).getAbsolutePath();
  }

  public void execute() throws MojoExecutionException {
    if (!isInCorrectLocation(getFinalBaseDir())) {
      throw new MojoExecutionException(
          "This action cannot execute at this location (%s)"
              .formatted(getFinalBaseDir().getAbsolutePath()));
    }

    String className = classNameGenerator.generate(getClassName());
    try {
      classFileWriter.write(
          getFullDirectoryPath(getFinalBaseDir()),
          className,
          classContentsGenerator.generate(className));
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
  }
}
