package io.openaev.service.xtm_auth;

import io.openaev.config.OpenAEVAdminConfig;
import jakarta.annotation.PostConstruct;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
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

  private static final byte[] HKDF_INFO = "openaev-jwks-keypair".getBytes();
  private static final int ED25519_SEED_LENGTH = 32;

  private final OpenAEVAdminConfig adminConfig;

  @Getter private KeyPair keyPair;
  @Getter private String kid;

  @PostConstruct
  void init() {
    try {
      Objects.requireNonNull(
          adminConfig.getEncryptionKey(),
          "openaev.admin.encryption_key is required for XTM auth key derivation");

      byte[] seed = deriveEd25519Seed(adminConfig.getEncryptionKey());
      this.keyPair = buildJcaKeyPair(seed);
      this.kid = computeKid(seed);

      log.info("XTM auth key derived successfully (kid={})", kid);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to derive XTM auth Ed25519 key pair", e);
    }
  }

  // -- PRIVATE --

  /**
   * HKDF-SHA256 Extract-and-Expand (RFC 5869) to derive a 32-byte Ed25519 seed from the platform
   * encryption key.
   */
  private static byte[] deriveEd25519Seed(String encryptionKey) {
    byte[] ikm = encryptionKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] seed = new byte[ED25519_SEED_LENGTH];

    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
    hkdf.init(new HKDFParameters(ikm, /* salt: empty */ new byte[0], HKDF_INFO));
    hkdf.generateBytes(seed, 0, seed.length);
    return seed;
  }

  /**
   * Builds a JCA {@link KeyPair} from a raw Ed25519 seed using BouncyCastle's lightweight API, then
   * converts to standard JCA types via PKCS8/X509 encoding for jjwt compatibility.
   */
  private static KeyPair buildJcaKeyPair(byte[] seed) throws Exception {
    Ed25519PrivateKeyParameters bcPriv = new Ed25519PrivateKeyParameters(seed);
    Ed25519PublicKeyParameters bcPub = bcPriv.generatePublicKey();

    KeyFactory kf = KeyFactory.getInstance("Ed25519");

    PrivateKey privateKey =
        kf.generatePrivate(
            new PKCS8EncodedKeySpec(
                PrivateKeyInfoFactory.createPrivateKeyInfo(bcPriv).getEncoded()));

    PublicKey publicKey =
        kf.generatePublic(
            new X509EncodedKeySpec(
                SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(bcPub).getEncoded()));

    return new KeyPair(publicKey, privateKey);
  }

  /**
   * Computes a deterministic {@code kid} as {@code Base64url(SHA-256(rawPublicKeyBytes))}. The raw
   * public key is the 32-byte Ed25519 point, derived from the seed.
   */
  private static String computeKid(byte[] seed) throws NoSuchAlgorithmException {
    Ed25519PrivateKeyParameters bcPriv = new Ed25519PrivateKeyParameters(seed, 0);
    Ed25519PublicKeyParameters bcPub = bcPriv.generatePublicKey();
    byte[] rawPubBytes = bcPub.getEncoded(); // 32 bytes
    byte[] thumbprint = MessageDigest.getInstance("SHA-256").digest(rawPubBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint);
  }
}
