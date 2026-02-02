package io.openaev.service;

import io.openaev.engine.api.AverageConfiguration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsSecurityDomainService {

  /**
   * @param config the configuration of the widget
   * @return a config with the necessary fields
   */
  public AverageConfiguration setFieldsForQuery(AverageConfiguration config) {
    Map<String, String> fields = new HashMap<>();
    fields.put("domainField", "base_security_domains_side");
    fields.put("typeField", "inject_expectation_type");
    fields.put("statusField", "inject_expectation_status");
    config.setField(fields);
    return config;
  }
}
