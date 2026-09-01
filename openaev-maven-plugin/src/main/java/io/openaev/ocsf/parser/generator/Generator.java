package io.openaev.ocsf.parser.generator;

import static io.openaev.ocsf.parser.generator.emission.ClassGenerator.SCHEMA_PACKAGE_NAME;
import static io.openaev.ocsf.parser.schema.SchemaDimension.*;

import io.openaev.fs.ClassFileWriter;
import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.generator.emission.ClassClassGenerator;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.DatatypeClassGenerator;
import io.openaev.ocsf.parser.generator.emission.ObjectClassGenerator;
import io.openaev.ocsf.parser.generator.utility.ObjectNodeDeserialiserEmitter;
import io.openaev.ocsf.parser.generator.utility.OcsfClassUidEmitter;
import io.openaev.ocsf.parser.generator.utility.OcsfConverterEmitter;
import io.openaev.ocsf.parser.schema.SchemaSource;
import io.openaev.ocsf.parser.schema.source.ReferentialSource;
import io.openaev.ocsf.parser.schema.source.Source;
import io.openaev.utils.DictionaryHelper;
import io.openaev.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Generator {
  private final ClassClassGenerator classClassGenerator = new ClassClassGenerator();
  private final ObjectClassGenerator objectClassGenerator = new ObjectClassGenerator();
  private final DatatypeClassGenerator datatypeClassGenerator = new DatatypeClassGenerator();
  private final Map<String, ClassMetadata> tracker = new HashMap<>();
  private final StringUtils stringUtils = new StringUtils();
  private final ClassFileWriter classFileWriter = new ClassFileWriter();
  private final SchemaSource schemaSource;
  private final PluginContext pluginContext;

  public Generator(SchemaSource schemaSource, PluginContext pluginContext) {
    this.schemaSource = schemaSource;
    this.pluginContext = pluginContext;
  }

  /**
   * Generates the code for all classes, objects and data types of the OCSF specification at the
   * chosen version. This is done in two passes: first, gather the generated Java class name and
   * associate it with the OCSF name. Second, iterate over every item and output the Java code for
   * their respective Java classes.
   *
   * @throws IOException there was an issue reading or writing a local file.
   */
  public void generate() throws IOException {
    // first round; build the Java class dictionary
    for (Source src : schemaSource.getSources()) {
      switch (src.getDimension()) {
        case SINGLE_OBJECT ->
            tracker.put(
                src.getExtendedName(),
                objectClassGenerator.metadata(
                    schemaSource.getVersion(),
                    src.getName(),
                    src.get(),
                    src.getExtension(),
                    src.getOcsfClassUid()));
        case SINGLE_CLASS ->
            tracker.put(
                src.getExtendedName(),
                classClassGenerator.metadata(
                    schemaSource.getVersion(),
                    src.getName(),
                    src.get(),
                    src.getExtension(),
                    src.getOcsfClassUid()));
        case DATATYPES ->
            src.get()
                .propertyStream()
                .forEach(
                    prop ->
                        tracker.put(
                            prop.getKey(),
                            datatypeClassGenerator.metadata(
                                schemaSource.getVersion(),
                                prop.getKey(),
                                prop.getValue(),
                                src.getExtension(),
                                null)));
      }
    }

    DictionaryHelper helper =
        new DictionaryHelper(
            tracker, (ReferentialSource) schemaSource.getSource(DICTIONARY.name()));

    // second round: write source files
    for (ClassMetadata md : tracker.values()) {
      Path classDirectoryPath =
          pluginContext
              .getRootOpenAEVAPISourceDirectory()
              .resolve(stringUtils.packageToPath(md.classPackage()));
      switch (md.dimension()) {
        case SINGLE_OBJECT ->
            classFileWriter.overwrite(
                classDirectoryPath.toString(),
                md.className(),
                objectClassGenerator.emit(md, helper));
        case SINGLE_CLASS ->
            classFileWriter.overwrite(
                classDirectoryPath.toString(),
                md.className(),
                classClassGenerator.emit(md, helper));
        case DATATYPES ->
            classFileWriter.overwrite(
                classDirectoryPath.toString(),
                md.className(),
                datatypeClassGenerator.emit(md, helper));
      }
    }

    // --- generate helpers
    String helperClassPackage =
        stringUtils.toVersionedPackage(schemaSource.getVersion(), SCHEMA_PACKAGE_NAME);

    classFileWriter.overwrite(
        pluginContext
            .getRootOpenAEVAPISourceDirectory()
            .resolve(stringUtils.packageToPath(helperClassPackage))
            .toString(),
        "OcsfConverter",
        new OcsfConverterEmitter(tracker, helperClassPackage).emit());

    classFileWriter.overwrite(
        pluginContext
            .getRootOpenAEVAPISourceDirectory()
            .resolve(stringUtils.packageToPath(helperClassPackage))
            .toString(),
        "OcsfClassUid",
        new OcsfClassUidEmitter(tracker, helperClassPackage).emit());

    classFileWriter.overwrite(
        pluginContext
            .getRootOpenAEVAPISourceDirectory()
            .resolve(stringUtils.packageToPath(helperClassPackage))
            .toString(),
        "ObjectNodeDeserialiser",
        new ObjectNodeDeserialiserEmitter(helperClassPackage, tracker.get("json_t")).emit());
  }
}
