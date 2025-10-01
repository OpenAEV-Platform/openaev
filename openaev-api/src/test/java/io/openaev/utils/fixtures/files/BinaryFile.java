package io.openaev.utils.fixtures.files;

public class BinaryFile extends BaseFile<byte[]> {
  public BinaryFile(byte[] data, String name) {
    this(data, name, "application/octet-stream");
  }

  public BinaryFile(byte[] data, String name, String mimeType) {
    super(data, name, mimeType);
  }

  @Override
  public byte[] getContentBytes() {
    return getContent();
  }
}
