package io.openaev.database.model;

/** Content identified by the SHA-256 of its canonical form: same content, same hash. */
public interface ContentHashed {

  String getContentHash();

  void setContentHash(String contentHash);
}
