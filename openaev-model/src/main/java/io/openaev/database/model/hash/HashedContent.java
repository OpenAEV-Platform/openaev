package io.openaev.database.model;

/** The canonical form a payload type writes its own fields into, before it is hashed. */
public interface HashedContent {

  /** A value under its exported name; null and empty values are left out. */
  void put(String key, Object value);

  /** An attached file, entered by the hash of its bytes, never by its name. */
  void file(String key, Document file);
}
