package io.openaev.database.model.payload;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A payload version revoked in a tenant, by a tenant admin or by the catalogue: it no longer runs
 * in the tenant until lifted.
 */
@Getter
@Setter
@Entity
@Table(name = "payload_revocations")
@EntityListeners(PayloadRevocationListener.class)
public class PayloadRevocation implements TenantBase {

  public static final String REVOKED_BY_FILIGRAN = "Revoked by Filigran";

  public static PayloadRevocation byAdmin(Payload payload, String reason, User user) {
    return of(payload, reason, user, false);
  }

  public static PayloadRevocation byCatalogue(Payload payload) {
    return of(payload, REVOKED_BY_FILIGRAN, null, true);
  }

  private static PayloadRevocation of(
      Payload payload, String reason, User user, boolean byCatalogue) {
    PayloadRevocation revocation = new PayloadRevocation();
    revocation.setTenant(payload.getTenant());
    revocation.setPayload(payload);
    revocation.setReason(reason);
    revocation.setUser(user);
    revocation.setByCatalogue(byCatalogue);
    return revocation;
  }

  // -- ID --

  @Id
  @Column(name = "payload_revocation_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- REVOCATION --

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_id", updatable = false, nullable = false)
  @NotNull
  private Payload payload;

  @Column(name = "payload_revocation_reason", updatable = false)
  @NotBlank
  private String reason;

  @Column(name = "payload_revocation_by_catalogue", updatable = false)
  private boolean byCatalogue;

  // -- AUDIT --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_revocation_user", updatable = false)
  private User user;

  @Column(name = "payload_revocation_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();
}
