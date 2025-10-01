package io.openaev.database.raw;

import java.time.Instant;

public interface RawFinding {

  String getFinding_id();

  String getFinding_value();

  String getFinding_field();

  Instant getFinding_created_at();

  Instant getFinding_updated_at();

  String getFinding_type();

  String getFinding_inject_id();

  String getInject_exercise();

  String getScenario_id();

  String getAsset_id();
}
