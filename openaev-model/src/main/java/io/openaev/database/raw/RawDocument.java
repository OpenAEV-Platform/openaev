package io.openaev.database.raw;

import java.util.List;

/**
 * Spring Data projection interface for document data.
 *
 * <p>This interface defines a projection for retrieving document metadata and associations.
 * Documents can be attached to exercises and scenarios, and are stored in MinIO/S3.
 *
 * @see io.openaev.database.model.Document
 */
public interface RawDocument {

  /**
   * Returns the unique identifier of the document.
   *
   * @return the document ID
   */
  String getDocument_id();

  /**
   * Returns the display name of the document.
   *
   * @return the document name
   */
  String getDocument_name();

  /**
   * Returns the description of the document.
   *
   * @return the document description, or {@code null} if not set
   */
  String getDocument_description();

  /**
   * Returns the MIME type of the document.
   *
   * @return the document MIME type (e.g., "application/pdf", "image/png")
   */
  String getDocument_type();

  /**
   * Returns the storage target path for the document.
   *
   * @return the storage path in MinIO/S3
   */
  String getDocument_target();

  /**
   * Returns the list of tag IDs associated with this document.
   *
   * @return list of tag IDs
   */
  List<String> getDocument_tags();

  /**
   * Returns the list of exercise IDs this document is attached to.
   *
   * @return list of exercise IDs
   */
  List<String> getDocument_exercises();

  /**
   * Returns the list of scenario IDs this document is attached to.
   *
   * @return list of scenario IDs
   */
  List<String> getDocument_scenarios();
}
