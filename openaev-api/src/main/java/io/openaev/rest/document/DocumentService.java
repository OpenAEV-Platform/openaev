package io.openaev.rest.document;

import io.openaev.context.ExecState;
import io.openaev.context.StateExecutionContext;
import io.openaev.context.TenantProxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentService {

  private final DocumentServiceInternal internal;

  public DocumentServiceInternal forOp(ExecState state) {
    return TenantProxy.of(internal, DocumentServiceInternal.class, state);
  }

  @Deprecated(since = "migration", forRemoval = true)
  public DocumentServiceInternal forCurrentTenant() {
    ExecState state = StateExecutionContext.get();
    if (state == null) {
      throw new IllegalStateException(
          "No StateExecutionContext active — use forOp(ExecState) instead");
    }
    return forOp(state);
  }

  public static String encodeFileName(String name) {
    return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
