package io.openaev.ocsf.parser;

import io.openaev.fs.ClassFileWriter;
import io.openaev.migration.ClassContentsGenerator;
import io.openaev.migration.ClassNameGenerator;
import io.openaev.ocsf.parser.generator.Generator;
import io.openaev.ocsf.parser.schema.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Generate source files for a java OCSF parser */
@Slf4j
@Mojo(name = "generate-ocsf-parser", defaultPhase = LifecyclePhase.NONE, aggregator = true)
public class GenerateParser extends AbstractMojo {
  @Parameter(
      property = "basedir",
      name = "basedir",
      required = true,
      defaultValue = "${maven.multiModuleProjectDirectory}")
  private File basedir;

  @Parameter(property = "reason", name = "reason", required = true, defaultValue = "migration")
  private String reason;

  @Parameter(
      property = "version",
      name = "version",
      defaultValue = OcsfSchemaVersionString.STRING_1_9_0)
  private String version;

  private final ClassNameGenerator classNameGenerator;
  private final ClassContentsGenerator classContentsGenerator;
  private final ClassFileWriter classFileWriter;

  private final File defaultBaseDir;

  public GenerateParser() {
    this(
        new ClassNameGenerator(),
        new ClassFileWriter(),
        new ClassContentsGenerator(),
        new File(""),
        "migration");
  }

  public GenerateParser(File baseDir, String reason) {
    this(
        new ClassNameGenerator(),
        new ClassFileWriter(),
        new ClassContentsGenerator(),
        baseDir,
        reason);
  }

  public GenerateParser(
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
  private final String subLocation = "src/main/java";

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
    Path path = Paths.get(getFinalBaseDir().getAbsoluteFile().toURI());
    PluginContext ctx =
        new PluginContext(
            path.resolve("openaev-maven-plugin/src/main/resources"),
            path.resolve("openaev-model").resolve(subLocation));
    try {
      SchemaSource schemaSource = Ocsf.schema(OcsfSchemaVersion.fromString(version), ctx);
      Generator generator = new Generator(schemaSource, ctx);
      generator.generate();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
