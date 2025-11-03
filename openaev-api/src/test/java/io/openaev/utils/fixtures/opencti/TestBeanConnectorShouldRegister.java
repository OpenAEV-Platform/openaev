package io.openaev.utils.fixtures.opencti;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TestBeanConnectorShouldRegister extends TestBeanConnector {

  @Override
  public boolean shouldRegister() {
    return true;
  }
}
