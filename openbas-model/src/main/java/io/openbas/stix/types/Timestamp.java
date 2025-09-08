package io.openbas.stix.types;

import java.time.Instant;

public class Timestamp extends BaseType<Instant> {
  public Timestamp(Instant value) {
    super(value);
  }
}
