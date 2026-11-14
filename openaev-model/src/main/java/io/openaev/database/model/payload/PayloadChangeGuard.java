package io.openaev.database.model.payload;

import io.openaev.database.model.Payload;
import io.openaev.exception.CatalogPayloadReadOnlyException;
import jakarta.persistence.PreUpdate;

/** Refuses changing a catalogue version, whatever changes it, so it stays what was released. */
public class PayloadChangeGuard {

  @PreUpdate
  void refuseIfManaged(Payload payload) {
    if (payload.isManaged()) {
      throw CatalogPayloadReadOnlyException.onChange();
    }
  }
}
