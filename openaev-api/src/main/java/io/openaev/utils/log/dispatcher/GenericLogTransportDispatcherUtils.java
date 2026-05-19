package io.openaev.utils.log.dispatcher;

import io.openaev.utils.log.transport.GenericLogTransportUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenericLogTransportDispatcherUtils {
  private final List<GenericLogTransportUtils> beans;

  public boolean dispatch(String message, Object level) {
    boolean status = true;

    for (GenericLogTransportUtils bean : beans) {
      if (bean.isEnabled() && !bean.send(message, level)) {
        status = false;
      }
    }

    return status;
  }
}
