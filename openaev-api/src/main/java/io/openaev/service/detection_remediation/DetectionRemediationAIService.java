package io.openaev.service.detection_remediation;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.ee.Ee;
import jakarta.annotation.Resource;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationAIService {
  private static final String X_OPENAEV_CERTIFICATE = "X-OpenAEV-Certificate";
  private static final String CROWDSTRIKE_URI = "/remediation/crowdstrike";
  private static final String SPLUNK_URI = "/remediation/splunk";

  @Value("${remediation.detection.webservice:#{null}}")
  String REMEDIATION_DETECTION_WEBSERVICE;

  @Value("#{${remediation.detection.webservice.retry: null} ?: 3}")
  Integer RETRY_CONNECTION;

  @Value("#{${remediation.detection.webservice.retry.waiting.milliseconds: null} ?: 30000}")
  Long RETRY_CONNECTION_WAITING_MILLISECONDS;

  private final Ee ee;
  private final HttpClientFactory httpClientFactory;
  @Resource protected ObjectMapper mapper;

  public DetectionRemediationAIResponse callRemediationDetectionAIWebservice(
      DetectionRemediationRequest payload, String collectorType) {
    // Check if account has EE licence
    String certificate = ee.getEncodedCertificate();

    String url;
    Class<? extends DetectionRemediationAIResponse> classResponse;
    switch (collectorType) {
      case CollectorsUtils.CROWDSTRIKE -> {
        url = REMEDIATION_DETECTION_WEBSERVICE + CROWDSTRIKE_URI;
        classResponse = DetectionRemediationCrowdstrikeResponse.class;
      }
      case CollectorsUtils.SPLUNK -> {
        url = REMEDIATION_DETECTION_WEBSERVICE + SPLUNK_URI;
        classResponse = DetectionRemediationSplunkResponse.class;
      }
      case CollectorsUtils.MICROSOFT_DEFENDER ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft defender not implemented");

      case CollectorsUtils.MICROSOFT_SENTINEL ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft sentinel not implemented");
      default ->
          throw new IllegalStateException("Collector :\"" + collectorType + "\" unsupported");
    }

    String errorMessage =
        "Request to Remediation Detection AI Webservice " + collectorType + " failed: ";

    HttpPost httpPost = new HttpPost(url);

    httpPost.addHeader(X_OPENAEV_CERTIFICATE, certificate);
    httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);

    StringEntity httpBody;
    try {
      httpBody = new StringEntity(mapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to process JSON {} . Error: {} ",
          payload.getClass().getName(),
          e.getMessage(),
          e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, errorMessage + ": " + e.getMessage());
    }

    httpPost.setEntity(httpBody);

    String responseBody = callWebService(errorMessage, httpPost);

    try {
      return mapper.readValue(responseBody, classResponse);
    } catch (JsonProcessingException e) {
      log.error("Failed to parse JSON response {} . Error: {} ", classResponse, e.getMessage(), e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          errorMessage + "The external service returned an invalid response: " + e.getMessage());
    }
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    // Check if account has EE licence
    ee.getEncodedCertificate();

    String url = REMEDIATION_DETECTION_WEBSERVICE + "/health";
    String errorMessage = "Connection to Remediation Detection AI Webservice failed: ";

    HttpGet httpGet = new HttpGet(url);
    String responseBody = callWebService(errorMessage, httpGet);

    try {
      return mapper.readValue(responseBody, DetectionRemediationHealthResponse.class);
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to parse JSON response DetectionRemediationHealthResponse . Error: {} ",
          e.getMessage(),
          e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          errorMessage + "The external service returned an invalid response.");
    }
  }

  private <T extends HttpUriRequestBase> String callWebService(String errorMessage, T http) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      int retry = 0;
      String responseBody = null;

      while (retry < RETRY_CONNECTION && responseBody == null) {
        retry++;
        int finalRetry = retry;
        responseBody =
            httpClient.execute(
                http, response -> checkCodeResponse(errorMessage, response, finalRetry));
      }

      if (responseBody == null)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, errorMessage);

      return responseBody;

    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, errorMessage, ex);
    }
  }

  private String checkCodeResponse(String errorMessage, ClassicHttpResponse response, int retry) {
    try {
      int codeResponse = response.getCode();
      if (codeResponse >= 300) {
        HttpStatus httpStatus =
            HttpStatus.resolve(codeResponse) == null
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.resolve(codeResponse);
        assert httpStatus != null;

        log.warn(
            "{}n° {}. Status code: {}. Reason: {}. {}",
            errorMessage,
            retry,
            response.getCode(),
            response.getReasonPhrase(),
            retry < RETRY_CONNECTION
                ? " Try again in "
                    + RETRY_CONNECTION_WAITING_MILLISECONDS
                    + " milliseconds ("
                    + retry
                    + "/"
                    + RETRY_CONNECTION
                    + ")"
                : RETRY_CONNECTION + " attempt failed, call stoped.");

        if (retry >= RETRY_CONNECTION)
          throw new ResponseStatusException(httpStatus, errorMessage + response.getReasonPhrase());

        if (RETRY_CONNECTION_WAITING_MILLISECONDS <= 0) return null;

        Thread.sleep(RETRY_CONNECTION_WAITING_MILLISECONDS);

      } else {
        return EntityUtils.toString(response.getEntity());
      }

    } catch (ParseException | IOException e) {
      log.error(
          "Failed to parse response ClassicHttpResponse {} . Error: {} ",
          this.getClass().getName(),
          e.getMessage(),
          e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          errorMessage + "The external service returned an invalid response.");
    } catch (InterruptedException e) {
      log.error(
          "Thread sleep retry stoped : retry to call remediation detection webservice {} . Error: {} ",
          this.getClass().getName(),
          e.getMessage(),
          e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          errorMessage + "The operation was interrupted unexpectedly.");
    }

    return null;
  }
}
