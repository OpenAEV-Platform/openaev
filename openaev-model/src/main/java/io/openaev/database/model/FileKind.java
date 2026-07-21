package io.openaev.database.model;

/**
 * Nature of a stored file (physically still the {@code documents} table).
 *
 * <ul>
 *   <li>{@link #DOCUMENT} - a regular attachment (image, PDF, script...).
 *   <li>{@link #MALWARE_SAMPLE} - a sensitive sample stored encrypted at rest as a
 *       password-protected zip and decrypted on the fly by the implant before detonation.
 * </ul>
 */
public enum FileKind {
  DOCUMENT,
  MALWARE_SAMPLE,
}
