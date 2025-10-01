package io.openaev.injectors.email.service;

import static java.util.Spliterators.spliteratorUnknownSize;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.execution.ProtectUser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.springframework.stereotype.Component;

@Component
public class EmailPgp {

  public PGPPublicKey getUserPgpKey(ProtectUser user) throws IOException {
    String userPgpKey = user.getPgpKey();
    if (!hasLength(user.getPgpKey())) {
      throw new IllegalArgumentException(user.getEmail() + " has no PGP public key");
    }
    InputStream in = new ByteArrayInputStream(userPgpKey.getBytes());
    InputStream decoderStream = PGPUtil.getDecoderStream(in);
    PGPPublicKeyRing keyRing = new JcaPGPPublicKeyRing(decoderStream);
    Spliterator<PGPPublicKey> splitIterator =
        spliteratorUnknownSize(keyRing.getPublicKeys(), Spliterator.ORDERED);
    Stream<PGPPublicKey> targetStream = StreamSupport.stream(splitIterator, false);
    return targetStream
        .filter(pgpPublicKey -> pgpPublicKey.isEncryptionKey() && !pgpPublicKey.isMasterKey())
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException(user.getEmail() + " has invalid PGP public key"));
  }

  public String encrypt(PGPPublicKey encKey, String clearData) {
    return new String(encrypt(clearData.getBytes(), encKey), StandardCharsets.UTF_8);
  }

  private byte[] encrypt(byte[] clearData, PGPPublicKey encKey) {
    try {
      byte[] compressedData = compress(clearData);
      ByteArrayOutputStream bOut = new ByteArrayOutputStream();
      OutputStream out = new ArmoredOutputStream(bOut);
      PGPEncryptedDataGenerator encGen =
          new PGPEncryptedDataGenerator(
              new BcPGPDataEncryptorBuilder(PGPEncryptedDataGenerator.CAST5)
                  .setSecureRandom(new SecureRandom())
                  .setWithIntegrityPacket(true));
      encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(encKey));
      OutputStream encOut = encGen.open(out, compressedData.length);
      encOut.write(compressedData);
      encOut.close();
      out.close();
      return bOut.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] compress(byte[] clearData) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    PGPCompressedDataGenerator comData =
        new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
    OutputStream cos = comData.open(bOut); // open it with the final destination
    PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
    OutputStream pOut =
        lData.open(
            cos, // the compressed output stream
            PGPLiteralData.BINARY,
            PGPLiteralData.CONSOLE,
            clearData.length, // length of clear data
            new Date() // current time
            );
    pOut.write(clearData);
    pOut.close();
    comData.close();
    return bOut.toByteArray();
  }
}
