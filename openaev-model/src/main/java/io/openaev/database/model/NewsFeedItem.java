package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * An XTM Hub news feed item delivered to a single user.
 *
 * <p>One row per user and Hub item: the Hub items are platform-wide, so the row carries no tenant.
 * Implements {@link UserScoped} so SSE delivery is restricted to the owning user.
 *
 * <p>{@link DynamicUpdate} because the user marking an item read and the collector refreshing its
 * title run in separate transactions on the same row: with the default full-row update, whichever
 * commits second would write back the stale value of the column the other changed (an item turned
 * unread again, or a refreshed title lost).
 */
@Entity
@Getter
@Setter
@DynamicUpdate
@Table(name = "news_feed_items")
@EntityListeners({ModelBaseListener.class})
public class NewsFeedItem implements Base, UserScoped {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "news_feed_item_id")
  @JsonProperty("news_feed_item_id")
  @NotBlank
  private String id;

  @Column(name = "news_feed_item_hub_id")
  @JsonProperty("news_feed_item_hub_id")
  @NotBlank
  private String hubItemId;

  @Column(name = "news_feed_item_title")
  @JsonProperty("news_feed_item_title")
  @NotBlank
  @Queryable(searchable = true, sortable = true)
  private String title;

  @Column(name = "news_feed_item_type")
  @JsonProperty("news_feed_item_type")
  @NotBlank
  @Queryable(sortable = true)
  private String type;

  @Type(StringArrayType.class)
  @Column(name = "news_feed_item_tags", columnDefinition = "text[]")
  @JsonProperty("news_feed_item_tags")
  private String[] tags = new String[0];

  @Column(name = "news_feed_item_url_path")
  @JsonProperty("news_feed_item_url_path")
  private String urlPath;

  /**
   * The details XTM Hub sends with the item (description, image, authors...), as flat key/value
   * pairs, replaced in full when the item changes. Not serialized: the event published for each row
   * would load it one row at a time, and the outputs map it themselves.
   */
  @ElementCollection
  @CollectionTable(
      name = "news_feed_items_metadata",
      joinColumns = @JoinColumn(name = "news_feed_item_id"))
  @MapKeyColumn(name = "news_feed_item_metadata_key")
  @Column(name = "news_feed_item_metadata_value")
  @Fetch(FetchMode.SUBSELECT)
  @JsonIgnore
  private Map<String, String> metadata = new HashMap<>();

  @Column(name = "news_feed_item_creation_date")
  @JsonProperty("news_feed_item_creation_date")
  @NotNull
  @Queryable(sortable = true)
  private Instant creationDate;

  @Column(name = "news_feed_item_is_read")
  @JsonProperty("news_feed_item_is_read")
  @Queryable(sortable = true)
  private boolean read = false;

  // EAGER: the owner id must stay readable after the session closes (SSE user scoping)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  @JsonProperty("news_feed_item_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  @NotNull
  private User user;

  // Stored as a plain string: removing a {@link NewsFeedType} must not break loading old rows.
  public void setType(NewsFeedType type) {
    this.type = type.name();
  }

  @Override
  @JsonIgnore
  public String getOwnerUserId() {
    return this.user != null ? this.user.getId() : null;
  }

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return ResourceType.NEWS_FEED;
  }
}
