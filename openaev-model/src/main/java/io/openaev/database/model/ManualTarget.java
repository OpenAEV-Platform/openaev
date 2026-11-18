package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

/**
 * Inject target typed by hand in the inject content (an IP address, a subnet or a hostname) when
 * the content selects manual targeting. It is not backed by any entity, so it is resolved by the
 * {@code ManualTargetSearchAdaptor} from the inject content.
 *
 * <p>The id is derived from the value rather than being the value itself: it travels in URL paths
 * (target results), where a subnet's {@code /} would break the route.
 */
@Data
public class ManualTarget extends InjectTarget {

  public ManualTarget(String value) {
    this.setId(idOf(value));
    this.setName(value);
    this.setTags(Set.of());
    this.setTargetType("MANUAL");
  }

  @JsonProperty("target_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  /** Stable id of a manual target value, identical across calls for the same value. */
  public static String idOf(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
  }

  @Override
  protected String getTargetSubtype() {
    return "Manual";
  }
}
