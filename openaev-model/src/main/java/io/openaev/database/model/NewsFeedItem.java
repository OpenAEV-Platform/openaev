package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * An XTM Hub news feed item delivered to a single user.
 *
 * <p>One row per user and Hub item: the Hub items are platform-wide, so the row carries no tenant.
 * Implements {@link UserScoped} so SSE delivery is restricted to the owning user.
 */
@Entity
@Getter
@Setter
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
  private String title;

  @Column(name = "news_feed_item_type")
  @JsonProperty("news_feed_item_type")
  @NotBlank
  private String type;

  @Type(StringArrayType.class)
  @Column(name = "news_feed_item_tags", columnDefinition = "text[]")
  @JsonProperty("news_feed_item_tags")
  private String[] tags = new String[0];

  @Column(name = "news_feed_item_url_path")
  @JsonProperty("news_feed_item_url_path")
  private String urlPath;

  @Column(name = "news_feed_item_creation_date")
  @JsonProperty("news_feed_item_creation_date")
  @NotNull
  private Instant creationDate;

  @Column(name = "news_feed_item_is_read")
  @JsonProperty("news_feed_item_is_read")
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
