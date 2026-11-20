package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtm.counterpart.CounterpartService.Connection;

/** Whether the tenant is connected to a product, and where users open it. No credential. */
public record XtmConnectionOutput(
    @JsonProperty("xtm_connection_product") String product,
    @JsonProperty("xtm_connection_connected") boolean connected,
    @JsonProperty("xtm_connection_url") String url) {

  public static XtmConnectionOutput from(Connection c) {
    return new XtmConnectionOutput(c.product(), c.connected(), c.url());
  }
}
