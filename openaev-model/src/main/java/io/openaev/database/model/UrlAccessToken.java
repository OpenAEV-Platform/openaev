package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
@Setter
@Table(name = "url_access_token")
public class UrlAccessToken implements Base {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "id", nullable = false)
  @JsonProperty("url_access_token_id")
  @NotBlank
  private String id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  @JsonProperty("url_access_token_token_hash")
  @NotBlank
  private String tokenHash;

  @Column(name = "url", nullable = false)
  @JsonProperty("url_access_token_url")
  @NotBlank
  private String url;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  @NotNull
  @JsonIgnore
  private User user;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "exercise_id", nullable = false)
  @NotNull
  @JsonIgnore
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "creator_user_id")
  @JsonIgnore
  private User creatorUser;

  @Column(name = "expires_at", nullable = false)
  @JsonProperty("url_access_token_expires_at")
  @NotNull
  private Instant expiresAt;

  @Column(name = "revoked_at")
  @JsonProperty("url_access_token_revoked_at")
  private Instant revokedAt;

  @Column(name = "last_used_at")
  @JsonProperty("url_access_token_last_used_at")
  private Instant lastUsedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JsonProperty("url_access_token_created_at")
  @NotNull
  private Instant createdAt = now();
}
