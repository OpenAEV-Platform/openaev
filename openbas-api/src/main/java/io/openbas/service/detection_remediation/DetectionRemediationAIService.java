package io.openbas.service.detection_remediation;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.Gson;
import io.openbas.ee.Ee;
import io.openbas.utils.OkHttpClientUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationAIService {
  private static final String X_OPENAEV_CERTIFICATE = "X-OpenAEV-Certificate";
  private static final String REMEDIATION_DETECTION_WEBSERVICE_CROWDSTRIKE_URL =
      "remediation.detection.webservice.crowdstrike";
  private static final String REMEDIATION_DETECTION_WEBSERVICE_HEALTH_URL =
      "remediation.detection.webservice.health";

  private final OkHttpClientUtils okHttpClientUtils = new OkHttpClientUtils();
  private final Ee ee;
  private final Environment env;
  private final Gson json = new Gson();

  private final OkHttpClient CLIENT_RULES =
      new OkHttpClient.Builder()
          .callTimeout(2, TimeUnit.MINUTES) // Max time connection
          .readTimeout(2, TimeUnit.MINUTES) // Max time waiting an answer
          .build();

  private final OkHttpClient CLIENT_HEALTH = new OkHttpClient();

  public DetectionRemediationCrowdstrikeResponse callRemediationDetectionAIWebservice(
      DetectionRemediationRequest payload) {
    // Check if account has EE licence
    String certificate = ee.getEncodedCertificate();

    String url =
        Objects.requireNonNull(env.getProperty(REMEDIATION_DETECTION_WEBSERVICE_CROWDSTRIKE_URL));

    RequestBody body =
        RequestBody.create(json.toJson(payload), MediaType.parse(APPLICATION_JSON_VALUE));

    Request request =
        new Request.Builder()
            .url(url)
            .addHeader(X_OPENAEV_CERTIFICATE, certificate)
            .addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .method("POST", body)
            .build();

    String errorMessage = "Request to Remediation Detection AI Webservice failed: ";

    return this.okHttpClientUtils.call(
        CLIENT_RULES, request, errorMessage, json, DetectionRemediationCrowdstrikeResponse.class);
  }

  @Operation(summary = "Get the status of the remediation-detection web service")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Web service status successfully retrieved"),
        @ApiResponse(
            responseCode = "503",
            description = "Web service is not deployed on this instance")
      })
  public DetectionRemediationHealthResponse checkHealthWebservice() {
    // Check if account has EE licence
    ee.getEncodedCertificate();

    String url =
        Objects.requireNonNull(env.getProperty(REMEDIATION_DETECTION_WEBSERVICE_HEALTH_URL));

    Request request = new Request.Builder().url(url).method("GET", null).build();

    String errorMessage = "Connection to Remediation Detection AI Webservice failed: ";

    return this.okHttpClientUtils.call(
        CLIENT_HEALTH, request, errorMessage, json, DetectionRemediationHealthResponse.class);
  }
}
