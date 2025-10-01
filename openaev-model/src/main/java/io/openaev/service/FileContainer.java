package io.openaev.service;

import java.io.InputStream;

public class FileContainer {

  private String name;

  private String contentType;

  private InputStream inputStream;

  public FileContainer(String name, String contentType, InputStream inputStream) {
    this.name = name;
    this.contentType = contentType;
    this.inputStream = inputStream;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }
}
