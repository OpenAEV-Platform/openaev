package io.openaev.rest.rbac;

import io.openaev.aop.AccessControl;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.RequestMethod;

@AllArgsConstructor
@Getter
public class EndpointInfo {
  private final RequestMethod method;
  private final String path;
  private final AccessControl accessControl;
  private final List<String> consumes;

  @Override
  public String toString() {
    return method
        + " "
        + path
        + " (RBAC: "
        + accessControl.actionPerformed()
        + " "
        + accessControl.resourceType()
        + ")";
  }
}
