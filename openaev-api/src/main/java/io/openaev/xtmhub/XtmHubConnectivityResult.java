package io.openaev.xtmhub;

/**
 * What an XTM Hub connectivity refresh answers for one registration. {@code commercialModel} and
 * {@code organizationName} are null when the hub does not return them (older hub, unreachable hub,
 * unknown value).
 */
public record XtmHubConnectivityResult(
    XtmHubConnectivityStatus status,
    XtmHubCommercialModel commercialModel,
    String organizationName) {

  public static final XtmHubConnectivityResult INACTIVE =
      new XtmHubConnectivityResult(XtmHubConnectivityStatus.INACTIVE, null, null);
}
