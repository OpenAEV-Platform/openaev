package io.openaev.docs;

import io.openaev.docs.DocsLinksGenerator.InvalidDocsLinksException;
import io.openaev.docs.DocsLinksGenerator.LinksMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Keeps the committed Java class of docs link constants in line with {@code docs/links.json}: one
 * compile-time constant per key, so that backend code links to the documentation by key and a
 * renamed or removed key fails compilation instead of shipping a dead link.
 *
 * <p>The class is committed, not generated under {@code target/}, so that IDE builds that do not
 * run Maven goals compile without it. A local build rewrites it when the map changed (the developer
 * commits it with the map); a CI build fails instead, so a stale class cannot be merged.
 */
@Mojo(name = "docs-links", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateDocsLinks extends AbstractMojo {

  /** Maven command that rewrites the class, given in the CI failure message. */
  static final String UPDATE_COMMAND =
      "mvn -pl openaev-api -am -Dmaven.test.skip=true -Dopenaev.docs.check.skip=true package";

  static final String MESSAGE_OUT_OF_DATE =
      " is out of date with "
          + DocsLinksGenerator.LINKS_FILE_NAME
          + ": run `"
          + UPDATE_COMMAND
          + "` locally, which rewrites it, and commit it.";
  static final String MESSAGE_UPDATED = "): commit it.";
  static final String MESSAGE_UNKNOWN_URLS = "Docs URLs that are not links of ";

  /**
   * The links map. Set only in the POM, not from the command line: the goal rewrites the committed
   * class from it, so pointing it at another map would silently change that class.
   */
  @Parameter(required = true, defaultValue = "${project.basedir}/../docs/links.json")
  private File linksFile;

  @Parameter(required = true, defaultValue = "${project.basedir}/src/main/java")
  private File sourceDirectory;

  @Parameter(required = true, defaultValue = "io.openaev.docs")
  private String packageName;

  @Parameter(required = true, defaultValue = "DocsLinks")
  private String className;

  /**
   * Files whose docs URLs must all be links of the map, e.g. the {@code see also} comments of
   * {@code application.properties}, which keep full URLs for operators.
   */
  @Parameter private List<File> linkedFiles = new ArrayList<>();

  /** Repository root, only to show paths relative to it in messages. */
  @Parameter(defaultValue = "${project.basedir}/..", readonly = true)
  private File rootDirectory;

  private final Map<String, String> env;
  private final DocsLinksGenerator generator = new DocsLinksGenerator();

  public GenerateDocsLinks() {
    this.env = System.getenv();
  }

  public GenerateDocsLinks(
      File linksFile,
      File sourceDirectory,
      File rootDirectory,
      List<File> linkedFiles,
      Map<String, String> env) {
    this.linksFile = linksFile;
    this.sourceDirectory = sourceDirectory;
    this.rootDirectory = rootDirectory;
    this.linkedFiles = linkedFiles;
    this.env = env;
    this.packageName = "io.openaev.docs";
    this.className = "DocsLinks";
  }

  public void execute() throws MojoExecutionException, MojoFailureException {
    LinksMap links;
    try {
      links = generator.read(linksFile.toPath());
    } catch (InvalidDocsLinksException e) {
      throw new MojoFailureException(e.getMessage());
    }
    checkLinkedFiles(links);

    String contents = generator.generate(links.urls(), packageName, className);
    Path target =
        sourceDirectory
            .toPath()
            .resolve(packageName.replace('.', '/'))
            .resolve(className + ".java");
    String current;
    try {
      current = Files.readString(target, StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      current = null;
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot read " + target, e);
    }
    // Left untouched when up to date: a rewrite would bump its timestamp and force a recompilation.
    if (contents.equals(current)) {
      getLog().debug(target + " is up to date with " + linksFile);
      return;
    }
    String name = relativeToRoot(target);
    if (Ci.isCi(env)) {
      throw new MojoFailureException(name + MESSAGE_OUT_OF_DATE);
    }
    try {
      Files.createDirectories(target.getParent());
      Files.writeString(target, contents, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot write " + target, e);
    }
    getLog()
        .warn(
            "Updated "
                + name
                + " from "
                + DocsLinksGenerator.LINKS_FILE_NAME
                + " ("
                + links.urls().size()
                + " docs links"
                + MESSAGE_UPDATED);
  }

  private void checkLinkedFiles(LinksMap links)
      throws MojoExecutionException, MojoFailureException {
    List<String> unknown = new ArrayList<>();
    for (File file : linkedFiles) {
      try {
        unknown.addAll(generator.unknownLinks(file.toPath(), links));
      } catch (IOException e) {
        throw new MojoExecutionException("Cannot read " + file, e);
      }
    }
    if (!unknown.isEmpty()) {
      throw new MojoFailureException(
          MESSAGE_UNKNOWN_URLS
              + DocsLinksGenerator.LINKS_FILE_NAME
              + ":\n  - "
              + String.join("\n  - ", unknown));
    }
  }

  /** Path relative to the repository root with {@code /} separators, as shown in messages. */
  private String relativeToRoot(Path path) {
    Path file = path.toAbsolutePath().normalize();
    if (rootDirectory != null) {
      Path root = rootDirectory.toPath().toAbsolutePath().normalize();
      if (file.startsWith(root)) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
      }
    }
    return file.toString();
  }
}
