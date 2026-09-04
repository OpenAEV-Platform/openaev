package io.openaev.ocsf.parser;

import io.openaev.ocsf.parser.generator.Generator;
import io.openaev.ocsf.parser.schema.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
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

  @Parameter(
      property = "version",
      name = "version",
      defaultValue = OcsfSchemaVersionString.STRING_1_9_0)
  private String version;

  private final File defaultBaseDir;

  public GenerateParser() {
    this(new File(""));
  }

  public GenerateParser(File baseDir) {
    this.defaultBaseDir = baseDir;
  }

  private File getFinalBaseDir() {
    return basedir == null ? defaultBaseDir : basedir;
  }

  public void execute() throws MojoExecutionException {
    Path path = Paths.get(getFinalBaseDir().getAbsoluteFile().toURI());
    String subLocation = "src/main/java";
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
