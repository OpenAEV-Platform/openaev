package io.openbas.rest.rbac;

import io.openbas.aop.RBAC;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.RequestMethod;

@AllArgsConstructor
@Getter
public class EndpointInfo {
  private final RequestMethod method;
  private final String path;
  private final RBAC rbac;
  private final List<String> consumes;

  @Override
  public String toString() {
    return method
        + " "
        + path
        + " (RBAC: "
        + rbac.actionPerformed()
        + " "
        + rbac.resourceType()
        + ")";
  }
}
