package io.openaev.database.raw;

import java.util.List;

public interface RawPayloadRelatedIds {

  String getPayload_id();

  List<String> getAttack_pattern_ids();

  List<String> getDomain_ids();

  List<String> getTag_ids();
}
