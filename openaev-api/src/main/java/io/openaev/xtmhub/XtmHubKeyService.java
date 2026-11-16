package io.openaev.xtmhub;

import static io.openaev.database.model.SettingKeys.XTM_HUB_KEY_VERSION;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.database.model.Setting;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.Ed25519KeyUtils;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The instance's key for XTM Hub calls: one Ed25519 key for the whole instance, every tenant
 * included, derived from {@code openaev.admin.encryption_key} and the {@code xtm_hub_key_version}
 * platform setting. Nothing is stored, so every node derives the same key and a restart never
 * forces a new enrollment. Another version, or another encryption key, gives another key, which the
 * Hub learns only through a new registration by an admin.
 */
@Service
@RequiredArgsConstructor
public class XtmHubKeyService {

  static final String HKDF_LABEL_PREFIX = "openaev:xtm-hub:platform-assertion::v";
  static final String ASSERTION_AUDIENCE = "xtm-hub";
  static final String ASSERTION_IDENTIFIER = "openaev";
  static final String IDENTIFIER_CLAIM = "identifier";
  static final String TENANT_ID_CLAIM = "tenant_id";
  static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(5);

  private final OpenAEVAdminConfig adminConfig;
  private final PlatformSettingsService platformSettingsService;

  private final Map<Integer, SigningKey> keysByVersion = new ConcurrentHashMap<>();

  @Transactional(readOnly = true)
  public XtmHubPublicJwk publicJwk() {
    SigningKey key = currentKey();
    return new XtmHubPublicJwk("OKP", "Ed25519", "EdDSA", "sig", key.kid(), key.x());
  }

  /**
   * A 5-minute assertion for one XTM Hub call ({@code XTM-Hub-Platform-Assertion} header), with a
   * unique {@code jti} the Hub consumes once.
   *
   * @param tenantId the tenant the call is made for, or {@code null} for a call made for the whole
   *     instance
   */
  @Transactional(readOnly = true)
  public String signAssertion(String platformId, @Nullable String tenantId) {
    Objects.requireNonNull(platformId, "platformId is required to sign an XTM Hub assertion");
    SigningKey key = currentKey();
    Instant now = Instant.now();
    JwtBuilder jwt =
        Jwts.builder()
            .header()
            .keyId(key.kid())
            .type("JWT")
            .and()
            .issuer(platformId)
            .audience()
            .single(ASSERTION_AUDIENCE)
            .claim(IDENTIFIER_CLAIM, ASSERTION_IDENTIFIER)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ASSERTION_LIFETIME)))
            .id(UUID.randomUUID().toString());
    if (tenantId != null) {
      jwt.claim(TENANT_ID_CLAIM, tenantId);
    }
    return jwt.signWith(key.privateKey(), Jwts.SIG.EdDSA).compact();
  }

  // -- PRIVATE --

  private SigningKey currentKey() {
    return keysByVersion.computeIfAbsent(currentVersion(), this::derive);
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

  private SigningKey derive(int version) {
    String encryptionKey =
        Objects.requireNonNull(
            adminConfig.getEncryptionKey(),
            "openaev.admin.encryption_key is required for the XTM Hub key derivation");
    byte[] seed =
        Ed25519KeyUtils.deriveSeed(
            encryptionKey, (HKDF_LABEL_PREFIX + version).getBytes(StandardCharsets.UTF_8));
    try {
      return new SigningKey(
          Ed25519KeyUtils.kid(seed),
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(Ed25519KeyUtils.rawPublicKey(seed)),
          Ed25519KeyUtils.keyPair(seed).getPrivate());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to derive the XTM Hub Ed25519 key", e);
    }
  }

  private record SigningKey(String kid, String x, PrivateKey privateKey) {}
}
