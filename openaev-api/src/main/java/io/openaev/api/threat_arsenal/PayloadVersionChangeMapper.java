package io.openaev.api.threat_arsenal;

import io.openaev.api.threat_arsenal.dto.PayloadVersionChangeOutput;
import io.openaev.service.payload.PayloadChange;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PayloadVersionChangeMapper {

  private PayloadVersionChangeMapper() {}

  /** The changed fields in the order the payload declares them. */
  public static List<PayloadVersionChangeOutput> toOutputs(PayloadChange change) {
    Set<String> fields = new LinkedHashSet<>();
    change.after().fieldNames().forEachRemaining(fields::add);
    change.before().fieldNames().forEachRemaining(fields::add);
    return fields.stream()
        .filter(change.keys()::contains)
        .map(
            field ->
                new PayloadVersionChangeOutput(
                    field, change.before().get(field), change.after().get(field)))
        .toList();
  }
}
