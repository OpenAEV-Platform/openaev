package io.openaev.service;

import java.io.InputStream;

/**
 * Container class that wraps a file with its metadata for download operations.
 *
 * <p>This class holds the file content along with its name and MIME type, making it suitable for
 * returning files from API endpoints with proper Content-Disposition and Content-Type headers.
 *
 * @see FileService#getFileContainer(String)
 */
public class FileContainer {

  /** The filename to use in Content-Disposition header. */
  private String name;

  /** The MIME type of the file. */
  private String contentType;

  /** The file content as an input stream. */
  private InputStream inputStream;

  /**
   * Constructs a new file container with all required attributes.
   *
   * @param name the filename
   * @param contentType the MIME type
   * @param inputStream the file content stream
   */
  public FileContainer(String name, String contentType, InputStream inputStream) {
    this.name = name;
    this.contentType = contentType;
    this.inputStream = inputStream;
  }

  /**
   * Returns the filename.
   *
   * @return the filename
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the filename.
   *
   * @param name the filename
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the MIME content type.
   *
   * @return the content type
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Sets the MIME content type.
   *
   * @param contentType the content type
   */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  /**
   * Returns the file content input stream.
   *
   * @return the input stream
   */
  public InputStream getInputStream() {
    return inputStream;
  }

  /**
   * Sets the file content input stream.
   *
   * @param inputStream the input stream
   */
  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }
}
