package io.openaev.schema;

import static io.openaev.schema.SchemaUtils.isValidClassName;

import io.openaev.aop.RBAC;
import io.openaev.engine.EngineContext;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.schema.model.PropertySchemaDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping
public class SchemaApi extends RestBehavior {

  private final EngineContext engineContext;

  @PostMapping("/api/schemas/{className}")
  @RBAC(skipRBAC = true)
  public List<PropertySchemaDTO> schemas(
      @PathVariable @NotNull final String className,
      @RequestParam final boolean filterableOnly,
      @RequestBody @Valid @NotNull List<String> filterNames)
      throws ClassNotFoundException {

    final String basePackage = "io.openaev.database.model";

    if (!isValidClassName(className)) {
      throw new IllegalArgumentException("Class not allowed : " + className);
    }
    String completeClassName = basePackage + "." + className;

    Class<?> clazz = Class.forName(completeClassName);

    return SchemaUtils.schemaWithSubtypes(clazz).stream()
        .filter(p -> !filterableOnly || p.isFilterable())
        .filter(p -> filterNames.isEmpty() || filterNames.contains(p.getJsonName()))
        .map(PropertySchemaDTO::new)
        .toList();
  }

  @GetMapping("/api/engine/schemas")
  @RBAC(skipRBAC = true)
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
