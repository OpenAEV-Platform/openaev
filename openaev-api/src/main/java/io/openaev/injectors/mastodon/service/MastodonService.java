package io.openaev.injectors.mastodon.service;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.DataAttachment;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.injectors.mastodon.config.MastodonConfig;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.ByteArrayBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MastodonService {

  @Resource private MastodonConfig config;
  @Resource private ObjectMapper mapper;
  private HttpClientFactory httpClientFactory;

  @Autowired
  public void setConfig(MastodonConfig config) {
    this.config = config;
  }

  @Autowired
  public void setHttpClientFactory(HttpClientFactory httpClientFactory) {
    this.httpClientFactory = httpClientFactory;
  }

  // public List<MastodonAttachment> resolveAttachments(Execution execution, List<Document>
  // attachments) {
  //     List<MastodonAttachment> resolved = new ArrayList<>();
  //     for (Document attachment : attachments) {
  //         String documentId = attachment.getId();
  //         Optional<Document> askedDocument = documentRepository.findById(documentId);
  //         try {
  //             Document doc = askedDocument.orElseThrow();
  //             InputStream fileInputStream = fileService.getFile(doc).orElseThrow();
  //             byte[] content = IOUtils.toByteArray(fileInputStream);
  //             resolved.add(new MastodonAttachment(doc.getName(), content, doc.getType()));
  //         } catch (Exception e) {
  //             // Can't fetch the attachments, ignore
  //             String docInfo = askedDocument.map(Document::getName).orElse(documentId);
  //             String message = "Error getting document " + docInfo;
  //             execution.addTrace(ExecutionTrace.traceError(getClass().getSimpleName(), message,
  // e));
  //         }
  //     }
  //     return resolved;
  // }

  public String sendStatus(
      Execution execution, String token, String status, List<DataAttachment> attachments)
      throws Exception {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      // upload files
      List<String> attachmentIds = new ArrayList<>();
      attachments.forEach(
          attachment -> {
            HttpPost httpPostAttachment = new HttpPost(config.getUrl() + "/api/v2/media");
            httpPostAttachment.addHeader("Authorization", "Bearer " + token);
            httpPostAttachment.addHeader("Accept", "*/*");
            MultipartEntityBuilder multipartEntity = MultipartEntityBuilder.create();
            HttpEntity entity =
                multipartEntity
                    .addPart(
                        "file",
                        new ByteArrayBody(
                            attachment.data(),
                            ContentType.create(attachment.contentType()),
                            attachment.name()))
                    .build();
            httpPostAttachment.setEntity(entity);
            try {
              httpClient.execute(
                  httpPostAttachment,
                  classicHttpResponse -> {
                    if (classicHttpResponse.getCode() == HttpStatus.SC_ACCEPTED) {
                      String body = EntityUtils.toString(classicHttpResponse.getEntity());
                      attachmentIds.add(mapper.readTree(body).get("id").asText());
                      return true;
                    } else {
                      execution.addTrace(
                          getNewErrorTrace(
                              "Cannot upload attachment "
                                  + attachment.name()
                                  + ": "
                                  + classicHttpResponse.getCode()
                                  + " "
                                  + classicHttpResponse.getReasonPhrase(),
                              ExecutionTraceAction.COMPLETE));
                      return false;
                    }
                  });
            } catch (IOException e) {
              execution.addTrace(
                  getNewErrorTrace(
                      "Cannot upload attachment " + attachment.name() + ": " + e.getMessage(),
                      ExecutionTraceAction.COMPLETE));
            }
          });
      Thread.sleep(3000);
      HttpPost httpPostStatus = new HttpPost(config.getUrl() + "/api/v1/statuses");
      httpPostStatus.addHeader("Authorization", "Bearer " + token);
      httpPostStatus.addHeader("Accept", "*/*");
      List<NameValuePair> params = new ArrayList<NameValuePair>();
      params.add(new BasicNameValuePair("status", status));
      attachmentIds.forEach(
          attachmentId -> {
            params.add(new BasicNameValuePair("media_ids[]", attachmentId));
          });
      httpPostStatus.setEntity(new UrlEncodedFormEntity(params));
      ClassicHttpResponse response =
          httpClient.execute(httpPostStatus, classicHttpResponse -> classicHttpResponse);
      if (response.getCode() == HttpStatus.SC_OK) {
        return "Mastodon status sent";
      } else {
        throw new Exception(
            response.getCode()
                + " "
                + response.getReasonPhrase()
                + ": "
                + response.getEntity().toString());
      }
    } catch (IOException e) {
      throw new ClientProtocolException(
          "Unexpected response for request on: " + config.getUrl(), e);
    }
  }
}
