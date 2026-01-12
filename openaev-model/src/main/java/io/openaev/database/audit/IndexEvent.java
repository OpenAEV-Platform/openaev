package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Event object representing a search index operation.
 *
 * <p>This lightweight event is used to trigger search index updates (particularly deletions) in
 * Elasticsearch or OpenSearch. Unlike {@link BaseEvent}, this event only carries the entity ID and
 * operation type, making it more efficient for index synchronization.
 *
 * <p>Index events are typically published after entity removal to ensure the search index stays
 * synchronized with the database.
 *
 * @see BaseEvent
 * @see ModelBaseListener
 */
@Getter
public class IndexEvent {

  /** The ID of the entity affected by this index operation. */
  @JsonProperty("event_id")
  private String id;

  /** The type of index operation (e.g., DATA_DELETE_SUCCESS). */
  @JsonProperty("event_type")
  private String type;

  /**
   * Constructs a new index event.
   *
   * @param type the type of index operation
   * @param id the ID of the affected entity
   */
  public IndexEvent(String type, String id) {
    this.type = type;
    this.id = id;
  }
}
