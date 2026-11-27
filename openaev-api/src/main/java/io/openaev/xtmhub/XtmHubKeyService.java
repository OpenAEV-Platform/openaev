package io.openaev.xtmhub;

import static io.openaev.database.model.SettingKeys.XTM_HUB_KEY_VERSION;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.database.model.Setting;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.Ed25519KeyUtils;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The keys for XTM Hub calls: one Ed25519 key for each tenant, so the Hub binds a key to the
 * registration of that tenant alone. They derive from {@code openaev.admin.encryption_key}, the
 * {@code xtm_hub_key_version} platform setting and the tenant id. Nothing is stored, so every node
 * derives the same keys and a restart never forces a new enrollment. Another version, or another
 * encryption key, gives other keys, which the Hub learns only through a new registration by an
 * admin.
 */
@Service
@RequiredArgsConstructor
public class XtmHubKeyService {

  static final String TENANT_HKDF_LABEL_PREFIX = "openaev:xtm-hub:tenant-assertion::v";
  // The type XTM Hub requires: it names what the JWT is for, so another JWT is never taken for an
  // assertion.
  static final String ASSERTION_TYPE = "xtm-hub-platform+jwt";
  static final String ASSERTION_AUDIENCE = "xtm-hub";
  static final String ASSERTION_IDENTIFIER = "openaev";
  static final String IDENTIFIER_CLAIM = "identifier";
  static final String TENANT_ID_CLAIM = "tenant_id";
  static final Duration ASSERTION_LIFETIME = Duration.ofSeconds(60);
  static final int MIN_ENCRYPTION_KEY_LENGTH = 32;

  private final OpenAEVAdminConfig adminConfig;
  private final PlatformSettingsService platformSettingsService;
  private final TenantService tenantService;

  /**
   * The public key of one tenant. The id comes from the path of a public endpoint, so an unknown or
   * soft-deleted tenant gets no key.
   */
  @Transactional(readOnly = true)
  public Optional<PublicJwk<PublicKey>> tenantPublicJwk(String tenantId) {
    return tenantService.findActiveById(tenantId).map(tenant -> publicJwkOf(derive(tenantId)));
  }

  /**
   * A 60-second assertion for one XTM Hub call ({@code XTM-Hub-Platform-Assertion} header), with a
   * unique {@code jti} the Hub consumes once.
   *
   * @param tenantId the tenant the call is made for, signed with that tenant's key. It must be a
   *     tenant the caller resolved from its {@code TxCtx}, never one read from the request.
   */
  @Transactional(readOnly = true)
  public String signAssertion(String platformId, String tenantId) {
    Objects.requireNonNull(platformId, "platformId is required to sign an XTM Hub assertion");
    Objects.requireNonNull(tenantId, "tenantId is required to sign an XTM Hub assertion");
    SigningKey key = derive(tenantId);
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(key.kid())
        .type(ASSERTION_TYPE)
        .and()
        .issuer(platformId)
        .audience()
        .single(ASSERTION_AUDIENCE)
        .claim(IDENTIFIER_CLAIM, ASSERTION_IDENTIFIER)
        .claim(TENANT_ID_CLAIM, tenantId)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ASSERTION_LIFETIME)))
        .id(UUID.randomUUID().toString())
        .signWith(key.privateKey(), Jwts.SIG.EdDSA)
        .compact();
  }

  // -- PRIVATE --

  private static PublicJwk<PublicKey> publicJwkOf(SigningKey key) {
    return Jwks.builder()
        .key(key.publicKey())
        .id(key.kid())
        .algorithm("EdDSA")
        .publicKeyUse("sig")
        .build();
  }

  /**
   * Refuses a malformed version instead of falling back to the default: signing with another key
   * than the one the Hub holds would fail every call.
   */
  private int currentVersion() {
    String value =
        platformSettingsService
            .setting(XTM_HUB_KEY_VERSION.key())
            .map(Setting::getValue)
            .orElse(XTM_HUB_KEY_VERSION.defaultValue());
    try {
      int version = Integer.parseInt(value.trim());
      if (version >= 1) {
        return version;
      }
    } catch (NumberFormatException e) {
      // reported below
    }
    throw new IllegalStateException(
        XTM_HUB_KEY_VERSION.key() + " must be a positive integer, got: " + value);
  }

  /** Derived on each use, so no private key stays in memory. */
  private SigningKey derive(String tenantId) {
    int version = currentVersion();
    String encryptionKey =
        Objects.requireNonNull(
            adminConfig.getEncryptionKey(),
            "openaev.admin.encryption_key is required for the XTM Hub key derivation");
    if (encryptionKey.length() < MIN_ENCRYPTION_KEY_LENGTH) {
      throw new IllegalStateException(
          "openaev.admin.encryption_key must be at least "
              + MIN_ENCRYPTION_KEY_LENGTH
              + " characters to derive the XTM Hub keys");
    }
    String label = TENANT_HKDF_LABEL_PREFIX + version + "::" + tenantId;
    byte[] seed = Ed25519KeyUtils.deriveSeed(encryptionKey, label.getBytes(StandardCharsets.UTF_8));
    try {
      KeyPair keyPair = Ed25519KeyUtils.keyPair(seed);
      return new SigningKey(Ed25519KeyUtils.kid(seed), keyPair.getPublic(), keyPair.getPrivate());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to derive the XTM Hub Ed25519 key", e);
    }
  }

  private record SigningKey(String kid, PublicKey publicKey, PrivateKey privateKey) {}
}
