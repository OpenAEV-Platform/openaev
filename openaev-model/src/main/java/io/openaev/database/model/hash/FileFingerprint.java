package io.openaev.database.model.hash;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/** What a file is known by: the name it is stored under (MD5) and its content hash (SHA-256). */
public record FileFingerprint(String storageName, String contentHash) {

  private static final int BUFFER_SIZE = 8192;

  public static FileFingerprint of(byte[] bytes, String fileExtension) {
    try {
      return of(new ByteArrayInputStream(bytes), fileExtension);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Reads the stream once for both digests. */
  public static FileFingerprint of(InputStream bytes, String fileExtension) throws IOException {
    MessageDigest md5 = DigestUtils.getMd5Digest();
    MessageDigest sha256 = DigestUtils.getSha256Digest();
    byte[] buffer = new byte[BUFFER_SIZE];
    int read;
    while ((read = bytes.read(buffer)) != -1) {
      md5.update(buffer, 0, read);
      sha256.update(buffer, 0, read);
    }
    return new FileFingerprint(
        Hex.encodeHexString(md5.digest()) + "." + fileExtension,
        Hex.encodeHexString(sha256.digest()));
  }

  public static String contentHashOf(InputStream bytes) throws IOException {
    return DigestUtils.sha256Hex(bytes);
  }

  public static String contentHashOf(byte[] bytes) {
    return DigestUtils.sha256Hex(bytes);
  }
}
