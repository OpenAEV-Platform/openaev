package io.openaev.service.catalog.history;

import io.openaev.database.model.catalog.ContentChange;
import io.openaev.service.payload.PayloadVersionChange;
import java.util.Optional;

/** A change of a payload, with what its version changed when the change made one. */
public record PayloadHistoryEntry(
    ContentChange change, Optional<PayloadVersionChange> versionChange) {}
