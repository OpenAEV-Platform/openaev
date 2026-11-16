package io.openaev.service.catalog.payload;

import io.openaev.database.model.Payload;
import java.util.List;

/** What an install of several payloads did: the payloads installed, and the ones that failed. */
public record InstalledPayloads(List<Payload> installed, List<InstallablePayloadVersion> failed) {}
