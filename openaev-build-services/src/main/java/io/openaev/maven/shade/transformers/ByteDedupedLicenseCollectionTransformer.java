package io.openaev.maven.shade.transformers;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

/**
 * Collects all reputed {@code *LICENSE*} files, and collects a single copy of every licence in the
 * {@link LICENSE_DIR} directory. Deduplication of licence files based on their SHA-256 digest.
 */
public class ByteDedupedLicenseCollectionTransformer implements ReproducibleResourceTransformer {
  private static final String LICENSE_DIR = "META-INF/licenses";

  private record ResourceMetadata(String fileName, byte[] byteContents) {}

  private Map<String, ResourceMetadata> entries = new HashMap<>();
  private long time;

  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  @Override
  public void processResource(
      String resource, InputStream is, List<Relocator> relocators, long time) throws IOException {
    this.time = time;
    byte[] contents = is.readAllBytes();
    String fileName = new File(resource).getName();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(contents);
      entries.put(bytesToHex(digest), new ResourceMetadata(fileName, contents));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Could not load MessageDigest with SHA-256 algorithm", e);
    }
  }

  @Override
  public boolean canTransformResource(String resource) {
    return resource.contains("LICENSE") || resource.contains("DEPENDENCIES");
  }

  @Override
  public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException {
    processResource(resource, is, relocators, 0L);
  }

  @Override
  public boolean hasTransformedResource() {
    return true;
  }

  @Override
  public void modifyOutputStream(JarOutputStream jos) throws IOException {
    for (Map.Entry<String, ResourceMetadata> entry : entries.entrySet()) {
      String digest = entry.getKey();
      ResourceMetadata resourceMetadata = entry.getValue();
      JarEntry jarEntry =
          new JarEntry("%s/%s_%s.md".formatted(LICENSE_DIR, resourceMetadata.fileName(), digest));
      jarEntry.setTime(this.time);
      jos.putNextEntry(jarEntry);
      jos.write(resourceMetadata.byteContents());
      jos.flush();
    }
  }
}
