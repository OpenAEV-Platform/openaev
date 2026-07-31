package io.openaev.schema;

import static io.openaev.schema.SchemaUtils.isValidClassName;

import io.openaev.aop.AccessControl;
import io.openaev.engine.EngineContext;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.schema.model.PropertySchemaDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping
public class SchemaApi extends RestBehavior {

  /**
   * Public schema aliases used by clients and persisted filters.
   *
   * <ul>
   *   <li>{@code InjectExpectation} keeps backward compatibility after class rename.
   *   <li>{@code Credential} exposes credential_* fields (DTO contract) instead of
   *       secret_reference_* storage details.
   * </ul>
   */
  private static final Map<String, String> ENTITY_CLASS_ALIASES =
      Map.of(
          "InjectExpectation", "io.openaev.database.model.BaseInjectExpectation",
          "Credential", "io.openaev.api.credentials.form.CredentialOutput");

  private final EngineContext engineContext;

  @PostMapping("/api/schemas/{className}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public List<PropertySchemaDTO> schemas(
      @PathVariable @NotNull final String className,
      @RequestParam final boolean filterableOnly,
      @RequestBody @Valid @NotNull List<String> filterNames)
      throws ClassNotFoundException {

    final String basePackage = "io.openaev.database.model";

    if (!isValidClassName(className)) {
      throw new IllegalArgumentException("Class not allowed : " + className);
    }
    String completeClassName =
        ENTITY_CLASS_ALIASES.getOrDefault(className, basePackage + "." + className);

    Class<?> clazz = Class.forName(completeClassName);

    return SchemaUtils.schemaWithSubtypes(clazz).stream()
        .filter(p -> !filterableOnly || p.isFilterable())
        .filter(p -> filterNames.isEmpty() || filterNames.contains(p.getJsonName()))
        .map(PropertySchemaDTO::new)
        .toList();
  }

  @GetMapping("/api/engine/schemas")
  @Transactional
  @AccessControl(skipRBAC = true)
  public Set<PropertySchemaDTO> engineSchemas(
      @RequestParam(name = "classNames", required = false) List<String> classNames) {
    return engineContext.getModels().stream()
        .filter(
            model ->
                classNames == null || classNames.isEmpty() || classNames.contains(model.getName()))
        .flatMap(
            model -> {
              try {
                return SchemaUtils.schemaWithSubtypes(model.getModel()).stream();
              } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
              }
            })
        .filter(PropertySchema::isFilterable)
        .map(PropertySchemaDTO::new)
        .collect(Collectors.toSet());
  }
}
