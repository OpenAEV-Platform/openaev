package io.openaev.ee;

/** Why a Filigran XTM license certificate grants nothing (see {@link XtmLicenseVerifier}). */
public class XtmLicenseException extends Exception {

  public XtmLicenseException(String reason) {
    super(reason);
  }
}
