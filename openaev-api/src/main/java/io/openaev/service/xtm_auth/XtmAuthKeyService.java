package io.openaev.service.xtm_auth;

import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.utils.Ed25519KeyUtils;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Derives a deterministic Ed25519 key pair from the platform's encryption key using HKDF-SHA256.
 *
 * <p>This mirrors the key derivation approach used by OpenCTI: the same {@code encryption_key}
 * always produces the same key pair, making the service HA-safe and eliminating the need for
 * external key storage. The derived public key is exposed via the {@code /xtm/auth/jwks} endpoint
 * so that other Filigran products can verify JWTs emitted by this platform.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XtmAuthKeyService {

  private static final byte[] HKDF_INFO = "openaev-jwks-keypair".getBytes(StandardCharsets.UTF_8);

  private final OpenAEVAdminConfig adminConfig;

  @Getter private KeyPair keyPair;
  @Getter private String kid;

  @PostConstruct
  void init() {
    try {
      Objects.requireNonNull(
          adminConfig.getEncryptionKey(),
          "openaev.admin.encryption_key is required for XTM auth key derivation");

      byte[] seed = Ed25519KeyUtils.deriveSeed(adminConfig.getEncryptionKey(), HKDF_INFO);
      this.keyPair = Ed25519KeyUtils.keyPair(seed);
      this.kid = Ed25519KeyUtils.kid(seed);

      log.info("XTM auth key derived successfully (kid={})", kid);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to derive XTM auth Ed25519 key pair", e);
    }
  }
}
