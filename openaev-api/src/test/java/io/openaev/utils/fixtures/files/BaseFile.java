package io.openaev.utils.fixtures.files;

import lombok.Getter;

@Getter
public abstract class BaseFile<T> {
  private final T content;
  private final String fileName;
  private final String mimeType;

  public BaseFile(T content, String fileName, String mimeType) {
    this.content = content;
    this.fileName = fileName;
    this.mimeType = mimeType;
  }

  public String getMimeType() {
    return this.mimeType;
  }

  public abstract byte[] getContentBytes();

  public int getContentLength() {
    return getContentBytes().length;
  }
}
