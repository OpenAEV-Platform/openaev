package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** The last revocation list received from a repository; the lists apply to every tenant. */
@Getter
@Setter
@Entity
@Table(name = "revocation_list_states")
public class RevocationListState {

  @Id
  @Column(name = "revocation_list_repository")
  @JsonProperty("revocation_list_repository")
  private String repository;

  @Column(name = "revocation_list_serial")
  @JsonProperty("revocation_list_serial")
  private long serial;

  @Column(name = "revocation_list_issued_at")
  @JsonProperty("revocation_list_issued_at")
  private Instant issuedAt;

  // Not checked yet: an expired list blocks nothing until the lists are signed (RFC 0004 5.1).
  @Column(name = "revocation_list_expires_at")
  @JsonProperty("revocation_list_expires_at")
  private Instant expiresAt;

  @Column(name = "revocation_list_downloaded_at")
  @JsonProperty("revocation_list_downloaded_at")
  private Instant downloadedAt;
}
