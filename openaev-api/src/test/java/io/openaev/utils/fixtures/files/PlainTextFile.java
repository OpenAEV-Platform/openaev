package io.openaev.utils.fixtures.files;

public class PlainTextFile extends BaseFile<String> {
  public PlainTextFile(String content, String fileName) {
    super(content, fileName, "text/plain");
  }

  @Override
  public byte[] getContentBytes() {
    return getContent().getBytes();
  }
}
