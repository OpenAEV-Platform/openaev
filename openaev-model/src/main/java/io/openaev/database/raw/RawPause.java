package io.openaev.database.raw;

import java.time.Instant;

public interface RawPause {
  String getPause_id();

  String getPause_exercise();

  Instant getPause_date();

  long getPause_duration();
}
