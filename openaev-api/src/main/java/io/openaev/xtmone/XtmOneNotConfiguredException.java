package io.openaev.xtmone;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * XTM One is not configured on this OpenAEV (no URL or no token), so nothing can be asked of it.
 * Every XTM One chat route answers it the same way: a {@code 503} carrying {@link #MESSAGE} (see
 * {@code XtmOneChatApiExceptionHandler}); anywhere else it is resolved as the {@link
 * ResponseStatusException} it is, with the same status and message.
 */
public class XtmOneNotConfiguredException extends ResponseStatusException {

  public static final String MESSAGE = "XTM One is not configured";

  public XtmOneNotConfiguredException() {
    super(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
  }

  /**
   * The one "is XTM One configured?" guard of the chat routes, run by the chat API before anything
   * else and by the client before it calls XTM One.
   */
  public static void requireConfigured(XtmOneConfig config) {
    if (!config.isConfigured()) {
      throw new XtmOneNotConfiguredException();
    }
  }
}
