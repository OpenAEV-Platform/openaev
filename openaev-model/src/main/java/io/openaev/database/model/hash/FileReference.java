package io.openaev.database.model.hash;

/** A file known by its document ID: it enters the hash by the hash of its bytes. */
public record FileReference(String documentId) {}
