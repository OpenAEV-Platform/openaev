package io.openaev.injector_contract;

import java.io.InputStream;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an icon image for a contractor/injector.
 *
 * <p>Icons are displayed in the user interface to help identify different injector types. The icon
 * data is provided as an InputStream, typically loaded from resources.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * InputStream iconStream = getClass().getResourceAsStream("/icons/email.png");
 * ContractorIcon icon = new ContractorIcon(iconStream);
 * }</pre>
 *
 * @see Contractor
 */
@Getter
@Setter
public class ContractorIcon {

  /** The icon image data as an input stream (typically PNG format). */
  private InputStream data;

  /**
   * Creates a new contractor icon with the specified image data.
   *
   * @param data the icon image data as an input stream
   */
  public ContractorIcon(InputStream data) {
    this.data = data;
  }
}
