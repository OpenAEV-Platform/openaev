package io.openaev.api.xtmone;

import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneNotConfiguredException;
import io.openaev.xtmone.XtmOneUpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * How the XTM One chat routes ({@link XtmOneChatApi}, and them only) answer what stops them before
 * XTM One answers, in the body every relayed XTM One answer has: {@code {"detail": ...}}.
 */
@RestControllerAdvice(assignableTypes = XtmOneChatApi.class)
public class XtmOneChatApiExceptionHandler {

  /**
   * XTM One is not configured: every chat route, the reads, the writes and the message stream
   * alike, answers {@code 503} with {@link XtmOneNotConfiguredException#MESSAGE}.
   */
  @ExceptionHandler(XtmOneNotConfiguredException.class)
  public ResponseEntity<Object> handleNotConfigured(XtmOneNotConfiguredException e) {
    return XtmOneChatApi.relay(
        XtmOneClient.RelayedResponse.ofDetail(
            HttpStatus.SERVICE_UNAVAILABLE.value(), TextNode.valueOf(e.getReason())));
  }

  /**
   * XTM One refused a request whose answer the route does not relay itself (the agent list, a file
   * download): its refusal is answered as the relaying routes answer one.
   */
  @ExceptionHandler(XtmOneUpstreamException.class)
  public ResponseEntity<Object> handleUpstreamRefusal(XtmOneUpstreamException e) {
    return XtmOneChatApi.relay(e.getResponse());
  }
}
