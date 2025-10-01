package io.openaev.service;

import java.io.InputStream;
import java.util.zip.ZipEntry;

public class ImportEntry {

  private ZipEntry entry;

  private InputStream data;

  private long contentLength;

  public ImportEntry(ZipEntry entry, InputStream data, long contentLength) {
    this.entry = entry;
    this.data = data;
    this.contentLength = contentLength;
  }

  public ZipEntry getEntry() {
    return entry;
  }

  public void setEntry(ZipEntry entry) {
    this.entry = entry;
  }

  public InputStream getData() {
    return data;
  }

  public void setData(InputStream data) {
    this.data = data;
  }

  public long getContentLength() {
    return this.contentLength;
  }

  public void setContentLength(long contentLength) {
    this.contentLength = contentLength;
  }
}
