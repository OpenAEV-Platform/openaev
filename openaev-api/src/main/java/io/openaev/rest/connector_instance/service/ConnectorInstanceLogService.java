package io.openaev.rest.connector_instance.service;

import io.openaev.database.repository.ConnectorInstanceLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceLogService {

  private final ConnectorInstanceLogRepository connectorInstanceLogRepository;
}
