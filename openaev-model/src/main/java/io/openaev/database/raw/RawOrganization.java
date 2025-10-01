package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;

@SuppressWarnings("unused")
public interface RawOrganization {
  String getOrganization_id();

  String getOrganization_name();

  String getOrganization_description();

  Instant getOrganization_created_at();

  Instant getOrganization_updated_at();

  List<String> getOrganization_tags();

  List<String> getOrganization_injects();

  long getOrganization_injects_number();
}
