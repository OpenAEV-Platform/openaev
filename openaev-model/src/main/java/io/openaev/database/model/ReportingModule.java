package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single building block of a {@link Reporting} template, stored as a JSONB array element on the
 * reporting row. Modules are rendered in the order they appear in the array.
 *
 * <p>{@code @EqualsAndHashCode} matters here beyond style: the JSONB column's dirty-checking
 * compares the loaded and current {@code List<ReportingModule>} snapshots by value, and Object
 * identity equality made every load-then-flush cycle look "changed" even with no real edit. Once
 * {@code reportings} is tenant-active, that spurious UPDATE is scoped by the request's TxCtx; if
 * the flush happens outside a scope that covers the row's tenant, the inspector rewrite matches
 * zero rows and Hibernate throws a {@code StaleStateException} on a read that never wrote anything.
 */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ReportingModule implements Serializable {

  @JsonProperty("module_type")
  private ReportingModuleType moduleType;

  /** Optional display title overriding the default title of the module type. */
  @JsonProperty("module_title")
  private String moduleTitle;

  /** Free-form module configuration (e.g. {@code content} for CUSTOM_MARKDOWN). */
  @JsonProperty("module_config")
  private Map<String, Object> moduleConfig;
}
