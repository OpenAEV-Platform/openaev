package io.openaev.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;

/**
 * Ed25519 keys derived from the platform encryption key, so every node and every restart gets the
 * same key without storing it. Each use passes its own HKDF label: a key derived for one use never
 * signs for another.
 */
public final class Ed25519KeyUtils {

  private static final int ED25519_SEED_LENGTH = 32;

  private Ed25519KeyUtils() {}

  /** HKDF-SHA256 (RFC 5869), empty salt, the label as {@code info}: a 32-byte Ed25519 seed. */
  public static byte[] deriveSeed(String encryptionKey, byte[] label) {
    byte[] seed = new byte[ED25519_SEED_LENGTH];
    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
    hkdf.init(
        new HKDFParameters(encryptionKey.getBytes(StandardCharsets.UTF_8), new byte[0], label));
    hkdf.generateBytes(seed, 0, seed.length);
    return seed;
  }

  /** The JCA key pair of a seed, in the types jjwt signs with. */
  public static KeyPair keyPair(byte[] seed) throws GeneralSecurityException {
    try {
      Ed25519PrivateKeyParameters bcPrivate = new Ed25519PrivateKeyParameters(seed);
      Ed25519PublicKeyParameters bcPublic = bcPrivate.generatePublicKey();
      KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
      PrivateKey privateKey =
          keyFactory.generatePrivate(
              new PKCS8EncodedKeySpec(
                  PrivateKeyInfoFactory.createPrivateKeyInfo(bcPrivate).getEncoded()));
      PublicKey publicKey =
          keyFactory.generatePublic(
              new X509EncodedKeySpec(
                  SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(bcPublic).getEncoded()));
      return new KeyPair(publicKey, privateKey);
    } catch (IOException e) {
      throw new GeneralSecurityException("Cannot encode the Ed25519 key pair", e);
    }
  }

  /** The 32-byte public point, as a JWK carries it in {@code x}. */
  private static byte[] rawPublicKey(byte[] seed) {
    return new Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().getEncoded();
  }

  /** {@code base64url(SHA-256(raw public key))}. */
  public static String kid(byte[] seed) throws GeneralSecurityException {
    byte[] thumbprint = MessageDigest.getInstance("SHA-256").digest(rawPublicKey(seed));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint);
  }
}
