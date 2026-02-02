package io.openaev.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for tag data.
 *
 * <p>This interface defines a lightweight projection for retrieving tag information. Tags are used
 * to categorize and filter entities across the platform.
 *
 * @see io.openaev.database.model.Tag
 */
public interface RawTag {

  /**
   * Returns the unique identifier of the tag.
   *
   * @return the tag ID
   */
  String getTag_id();

  /**
   * Returns the display name of the tag.
   *
   * @return the tag name
   */
  String getTag_name();

  /**
   * Returns the color code for the tag.
   *
   * @return the color code (e.g., "#FF5722")
   */
  String getTag_color();

  /**
   * Returns the creation timestamp of the tag.
   *
   * @return the creation timestamp
   */
  Instant getTag_created_at();

  /**
   * Returns the last update timestamp of the tag.
   *
   * @return the update timestamp
   */
  Instant getTag_updated_at();
}
