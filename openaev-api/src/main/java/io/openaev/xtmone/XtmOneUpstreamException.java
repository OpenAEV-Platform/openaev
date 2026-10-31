package io.openaev.xtmone;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * XTM One refused a chat request whose answer is not relayed as it came (the agent list, a file
 * download, a streamed message). It carries what OpenAEV relays of that refusal, built like every
 * relayed answer (see {@link XtmOneClient#relayed}): the chat API answers it as such, and anywhere
 * else it is resolved as a {@link ResponseStatusException} of that status whose reason is XTM One's
 * {@code detail}.
 */
public class XtmOneUpstreamException extends ResponseStatusException {

  private final transient XtmOneClient.RelayedResponse response;

  public XtmOneUpstreamException(XtmOneClient.RelayedResponse response) {
    super(HttpStatusCode.valueOf(response.status()), response.detailText());
    this.response = response;
  }

  /** The refusal as the chat API relays it: its status and a body holding only {@code detail}. */
  public XtmOneClient.RelayedResponse getResponse() {
    return response;
  }
}
