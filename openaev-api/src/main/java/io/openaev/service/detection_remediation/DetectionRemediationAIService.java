package io.openaev.service.detection_remediation;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.ee.Ee;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.ConnectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationAIService {
  private static final String X_OPENAEV_CERTIFICATE = "X-OpenAEV-Certificate";

  @Value("${remediation.detection.webservice:#{null}}")
  String REMEDIATION_DETECTION_WEBSERVICE;

  private final Ee ee;
  private final HttpClientFactory httpClientFactory;
  @Resource protected ObjectMapper mapper;

  public DetectionRemediationCrowdstrikeResponse callRemediationDetectionAIWebservice(
      DetectionRemediationRequest payload) {
    // Check if account has EE licence
    String certificate = ee.getEncodedCertificate();

    String url = REMEDIATION_DETECTION_WEBSERVICE + "/remediation/crowdstrike";

    String errorMessage = "Request to Remediation Detection AI Webservice failed: ";

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {

      HttpPost httpPost = new HttpPost(url);

      httpPost.addHeader(X_OPENAEV_CERTIFICATE, certificate);
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);

      StringEntity httpBody = new StringEntity(mapper.writeValueAsString(payload));
      httpPost.setEntity(httpBody);

      String responseBody =
          httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));

      return mapper.readValue(responseBody, DetectionRemediationCrowdstrikeResponse.class);

    } catch (ConnectException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, errorMessage, ex);

    } catch (IOException e) {
      throw new RestClientException(errorMessage + e);
    }
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    // Check if account has EE licence
    ee.getEncodedCertificate();

    String url = REMEDIATION_DETECTION_WEBSERVICE + "/health";
    String errorMessage = "Connection to Remediation Detection AI Webservice failed: ";

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(url);
      String responseBody =
          httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));

      return mapper.readValue(responseBody, DetectionRemediationHealthResponse.class);

    } catch (ConnectException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, errorMessage, ex);

    } catch (IOException e) {
      throw new RestClientException(errorMessage + e);
    }
  }
}
