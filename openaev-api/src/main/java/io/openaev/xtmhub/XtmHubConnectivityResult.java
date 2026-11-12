package io.openaev.xtmhub;

/**
 * What an XTM Hub connectivity refresh answers for one registration. {@code commercialModel} is
 * null when the hub does not return it (older hub, unreachable hub, unknown value).
 */
public record XtmHubConnectivityResult(
    XtmHubConnectivityStatus status, XtmHubCommercialModel commercialModel) {

  public static final XtmHubConnectivityResult INACTIVE =
      new XtmHubConnectivityResult(XtmHubConnectivityStatus.INACTIVE, null);
}
