package io.openaev.opencti.service;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewSuccessTrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.*;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.client.response.fields.Error;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.PrivilegeService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.stix.objects.Bundle;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenCTIService {
  private final OpenCTIConfig classicOpenCTIConfig;
  private final OpenCTIClient openCTIClient;
  private final ObjectMapper mapper;
  private final PrivilegeService privilegeService;

  public QueryTypeFields.ResponsePayload queryTypeFields(ConnectorBase connector, String typeName)
      throws IOException, ConnectorError {
    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new QueryTypeFields(connector, typeName));
    if (r.isError()) {
      throw new ConnectorError(
          """
              Failed to query type fields for type %s with OpenCTI at %s
              Errors: %s
              """
              .formatted(
                  typeName,
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      return mapper.convertValue(r.getData(), QueryTypeFields.ResponsePayload.class);
    }
  }

  public RegisterConnector.ResponsePayload registerConnector(ConnectorBase connector)
      throws IOException, ConnectorError {
    privilegeService.ensurePrivilegedUserExistsForConnector(connector);

    QueryTypeFields.ResponsePayload typeFields = this.queryTypeFields(connector, "Connector");

    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new RegisterConnector(connector, typeFields.hasJwks()));
    if (r.isError()) {
      throw new ConnectorError(
          """
        Failed to register connector %s with OpenCTI at %s
        OpenCTI >= 6.8.9 is required with valid authentication token
        Errors: %s
        """
              .formatted(
                  connector.getName(),
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      RegisterConnector.ResponsePayload payload =
          mapper.convertValue(r.getData(), RegisterConnector.ResponsePayload.class);
      log.info(
          "Registered connector {} with OpenCTI at {}", connector.getName(), connector.getApiUrl());
      // side effect on transient state
      connector.setRegistered(true);
      if (payload.getRegisterConnectorContent() != null) {
        connector.setJwks(payload.getRegisterConnectorContent().getJwks());
      }
      return payload;
    }
  }

  public Ping.ResponsePayload pingConnector(ConnectorBase connector)
      throws IOException, ConnectorError {
    if (!connector.isRegistered()) {
      throw new ConnectorError(
          "Cannot ping connector %s with OpenCTI at %s: connector hasn't registered yet. Try again later."
              .formatted(connector.getName(), connector.getApiUrl()));
    }
    privilegeService.ensurePrivilegedUserExistsForConnector(connector);

    QueryTypeFields.ResponsePayload typeFields = this.queryTypeFields(connector, "Connector");

    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new Ping(connector, typeFields.hasJwks()));
    if (r.isError()) {
      throw new ConnectorError(
          """
        Failed to ping connector %s with OpenCTI at %s
        Errors: %s
        """
              .formatted(
                  connector.getName(),
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      Ping.ResponsePayload payload = mapper.convertValue(r.getData(), Ping.ResponsePayload.class);
      log.info(
          "Pinged connector {} with OpenCTI at {}", connector.getName(), connector.getApiUrl());
      if (payload.getPingConnectorContent() != null) {
        connector.setJwks(payload.getPingConnectorContent().getJwks());
      }
      return payload;
    }
  }

  public WorkToReceived.ResponsePayload workToReceived(
      ConnectorBase connector, String workId, String message) throws IOException, ConnectorError {
    if (!connector.isRegistered()) {
      throw new ConnectorError(
          "Cannot workToReceived via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
              .formatted(connector.getName(), connector.getApiUrl()));
    }

    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new WorkToReceived(workId, message));
    if (r.isError()) {
      throw new ConnectorError(
          """
                  Failed to acknowledge received %s with OpenCTI at %s
                  Errors: %s
                  """
              .formatted(
                  connector.getName(),
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      WorkToReceived.ResponsePayload payload =
          mapper.convertValue(r.getData(), WorkToReceived.ResponsePayload.class);
      log.info(
          "WorkToReceived connector {} with OpenCTI at {}",
          connector.getName(),
          connector.getApiUrl());
      return payload;
    }
  }

  public WorkToProcessed.ResponsePayload workToProcessed(
      ConnectorBase connector, String workId, String message, Boolean inError)
      throws IOException, ConnectorError {
    if (!connector.isRegistered()) {
      throw new ConnectorError(
          "Cannot workToProcessed via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
              .formatted(connector.getName(), connector.getApiUrl()));
    }

    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new WorkToProcessed(workId, message, inError));
    if (r.isError()) {
      throw new ConnectorError(
          """
                            Failed to acknowledge processed %s with OpenCTI at %s
                            Errors: %s
                            """
              .formatted(
                  connector.getName(),
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      WorkToProcessed.ResponsePayload payload =
          mapper.convertValue(r.getData(), WorkToProcessed.ResponsePayload.class);
      log.info(
          "WorkToProcessed connector {} with OpenCTI at {}",
          connector.getName(),
          connector.getApiUrl());
      return payload;
    }
  }

  public PushStixBundle.ResponsePayload pushStixBundle(Bundle bundle, ConnectorBase connector)
      throws IOException, ConnectorError {
    if (!connector.isRegistered()) {
      throw new ConnectorError(
          "Cannot push STIX bundle via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
              .formatted(connector.getName(), connector.getApiUrl()));
    }

    Response r =
        openCTIClient.execute(
            connector.getApiUrl(),
            classicOpenCTIConfig.getToken(),
            new PushStixBundle(connector, bundle.toStix(mapper)));
    if (r.isError()) {
      throw new ConnectorError(
          """
            Failed to push STIX bundle via connector %s to OpenCTI at %s
            Errors: %s
            """
              .formatted(
                  connector.getName(),
                  connector.getApiUrl(),
                  r.getErrors().stream().map(Error::toString).collect(Collectors.joining("\n"))));
    } else {
      PushStixBundle.ResponsePayload payload =
          mapper.convertValue(r.getData(), PushStixBundle.ResponsePayload.class);
      log.info(
          "Pushed STIX bundle via connector {} to OpenCTI at {}",
          connector.getName(),
          connector.getApiUrl());
      return payload;
    }
  }

  // TODO: support attachments; argument: `List<DataAttachment> attachments`
  public void createCase(
      Execution execution, String name, String description, List<DataAttachment> ignoredAttachments)
      throws Exception {
    Mutation mut = new CreateCase(name, description);
    Response response =
        openCTIClient.execute(
            classicOpenCTIConfig.getApiUrl(), classicOpenCTIConfig.getToken(), mut);
    if (response.getStatus() == HttpStatus.SC_OK) {
      execution.addTrace(
          getNewSuccessTrace(
              "Case created (" + response.getData() + ")", ExecutionTraceAction.COMPLETE));
    } else {
      execution.addTrace(getNewErrorTrace("Fail to POST", ExecutionTraceAction.COMPLETE));
    }
  }

  // TODO: support attachments; argument: `List<DataAttachment> attachments`
  public void createReport(
      Execution execution, String name, String description, List<DataAttachment> ignoredAttachments)
      throws IOException {
    Mutation mut = new CreateReport(name, description, Instant.now());
    Response response =
        openCTIClient.execute(
            classicOpenCTIConfig.getApiUrl(), classicOpenCTIConfig.getToken(), mut);
    if (response.getStatus() == HttpStatus.SC_OK) {
      execution.addTrace(
          getNewSuccessTrace(
              "Report created (" + response.getData() + ")", ExecutionTraceAction.COMPLETE));
    } else {
      execution.addTrace(getNewErrorTrace("Fail to POST", ExecutionTraceAction.COMPLETE));
    }
  }
}
