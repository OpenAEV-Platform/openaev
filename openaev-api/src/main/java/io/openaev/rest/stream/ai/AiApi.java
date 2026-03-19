package io.openaev.rest.stream.ai;

import static io.openaev.rest.stream.ai.AiPrompt.generatePrompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.RBAC;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class AiApi extends RestBehavior {
  public static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";
  private AiConfig aiConfig;

  @Autowired
  public void setAiConfig(AiConfig aiConfig) {
    this.aiConfig = aiConfig;
  }

  public ResponseEntity<Flux<AiResult>> queryAi(String body) {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException("AI mode is disabled");
    }
    @SuppressWarnings("resource")
    HttpClient client = HttpClient.newHttpClient();
    String uri =
        switch (aiConfig.getType()) {
          case "mistralai", "openai" -> aiConfig.getEndpoint() + "/v1/chat/completions";
          default -> throw new UnsupportedOperationException("Invalid ai type");
        };
    var request =
        HttpRequest.newBuilder(URI.create(uri))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Authorization", "Bearer " + aiConfig.getToken())
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .build();
    Flux<AiResult> dataFlux =
        Flux.create(
            objectFluxSink -> {
              try {
                CompletableFuture<HttpResponse<Void>> completableFuture =
                    client.sendAsync(
                        request,
                        responseInfo -> {
                          if (responseInfo.statusCode() == 200) {
                            return new AiSubscriber(
                                s -> {
                                  try {
                                    ObjectNode resultNode = mapper.readValue(s, ObjectNode.class);
                                    String id = resultNode.get("id").textValue();
                                    String content =
                                        resultNode
                                            .get("choices")
                                            .get(0)
                                            .get("delta")
                                            .get("content")
                                            .textValue();
                                    objectFluxSink.next(new AiResult(id, content));
                                  } catch (Exception e) {
                                    // Nothing to do
                                  }
                                });
                          } else {
                            throw new RuntimeException("Request failed");
                          }
                        });
                completableFuture.thenApply(
                    voidHttpResponse -> {
                      objectFluxSink.complete();
                      return "done";
                    });
              } catch (Exception e) {
                objectFluxSink.complete();
              }
            });
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(X_ACCEL_BUFFERING, "no")
        .body(dataFlux);
  }

  @PostMapping(path = "/api/ai/fix_spelling", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiFixSpelling(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text for any spelling mistakes and correct them accordingly in the original language of the text.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- If no mistake is detected, just return the original text without anything else.\n"
            + "- Do NOT change the length of the text.\n"
            + "- Your response should match the provided content format which is "
            + aiGenericTextInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format and the intended content.\n"
            + "\n"
            + " # Content\n"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/make_shorter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiMakeShorter(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text related to cybersecurity and cyber threat intelligence and make it shorter by dividing by 2 the size / length of the text or the number of paragraphs.\n"
            + "- Make it shorter by dividing by 2 the number of lines but you should keep the main ideas and concepts as well as original language of the text.\n"
            + "- Do NOT summarize nor enumerate points.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- Your response should match the provided content format which is "
            + aiGenericTextInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + "# Content"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/make_longer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiMakeLonger(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text related to cybersecurity and cyber threat intelligence and make it longer by doubling the size / length of the text or the number of paragraphs.\n"
            + "- Make it longer by doubling the number of lines by explaining concepts and developing the ideas but NOT too long, the final size should be twice the initial one.\n"
            + "- Respect the original language of the text.\n"
            + "- Do NOT summarize nor enumerate points.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- Your response should match the provided content format which is "
            + aiGenericTextInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + "# Content"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/change_tone", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiChangeTone(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text related to cybersecurity and cyber threat intelligence and change its tone to be more "
            + aiGenericTextInput.getTone()
            + ".\n"
            + "- Do NOT change the length of the text, the size of the output should be the same as the input.\n"
            + "- Do NOT summarize nor enumerate points.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- Your response should match the provided content in the same format which is "
            + aiGenericTextInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + "# Content"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiSummarize(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text related to cybersecurity and cyber threat intelligence and summarize it with main ideas and concepts.\n"
            + "- Make it shorter and summarize key points highlighting the deep meaning of the text.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- Your response should match the provided content format which is "
            + aiGenericTextInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + " # Content"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiExplain(
      @Valid @RequestBody final AiGenericTextInput aiGenericTextInput)
      throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- Examine the provided text related to cybersecurity and cyber threat intelligence and explain it.\n"
            + "- Popularize the text to enlighten non-specialist by explaining key concepts and overall meaning.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct. \n"
            + "- Your response should be done in plain text regardless of the original format.\n"
            + "\n"
            + " # Content"
            + aiGenericTextInput.getContent();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/generate_message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiGenerateMessage(
      @Valid @RequestBody final AiMessageInput aiMessageInput) throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- We are in the context of a cybersecurity breach and attack simulation or a cybersecurity crisis exercise.\n"
            + "- Examine the provided context related to a cybersecurity breach and attack simulation.\n"
            + "- You should generate a message from "
            + aiMessageInput.getSender()
            + " to "
            + aiMessageInput.getRecipient()
            + " given the provided input.\n"
            + "- The message should have a tone of "
            + aiMessageInput.getTone()
            + ".\n"
            + "- You should fake it and not writing about the simulation but like if it is a true cybersecurity threat and / or incident.\n"
            + "- The summary should have "
            + aiMessageInput.getParagraphs().toString()
            + " of approximately 5 lines each.\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- Your response should match the provided content format which is "
            + aiMessageInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + " # Context"
            + aiMessageInput.getContext()
            + "\n"
            + " # Input"
            + aiMessageInput.getInput();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/generate_subject", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiGenerateSubject(
      @Valid @RequestBody final AiMessageInput aiMessageInput) throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- We are in the context of a cybersecurity breach and attack simulation or a cybersecurity crisis exercise.\n"
            + "- Examine the provided context related to a cybersecurity breach and attack simulation.\n"
            + "- You should generate the subject of the email from "
            + aiMessageInput.getSender()
            + " to "
            + aiMessageInput.getRecipient()
            + " given the provided input.\n"
            + "- The subject should have a tone of "
            + aiMessageInput.getTone()
            + ".\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- You should fake it and not writing about the simulation but like if it is a true cybersecurity threat and / or incident.\n"
            + "- Your response should match the provided content format which is "
            + aiMessageInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + " # Context"
            + aiMessageInput.getContext()
            + "\n"
            + " # Input"
            + aiMessageInput.getInput();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }

  @PostMapping(path = "/api/ai/generate_media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<Flux<AiResult>> aiGenerateMedia(
      @Valid @RequestBody final AiMediaInput aiMediaInput) throws JsonProcessingException {
    if (!aiConfig.isEnabled()) {
      throw new UnsupportedOperationException(
          "AI is disabled in this platform, please ask your administrator.");
    }
    String prompt =
        "\n"
            + "# Instructions\n"
            + "- We are in the context of a cybersecurity breach and attack simulation or a cybersecurity crisis exercise.\n"
            + "- Examine the provided context related to a cybersecurity breach and attack simulation.\n"
            + "- You should generate an article as a journalist to put media pressure on the company given the provided input.\n"
            + "- The article should have a tone of "
            + aiMediaInput.getTone()
            + ".\n"
            + "- Ensure that all words are accurately spelled and that the grammar is correct.\n"
            + "- You should fake it and not writing about the simulation but like if it is a true cybersecurity threat and / or incident.\n"
            + "- The article should look like a true cybersecurity article in a newspaper.\n"
            + "- Your response should match the provided content format which is "
            + aiMediaInput.getFormat()
            + ". Be sure to respect this format and to NOT output anything else than the format.\n"
            + "\n"
            + " # Context"
            + aiMediaInput.getContext()
            + "\n"
            + " # Input"
            + aiMediaInput.getInput();
    AiQueryModel body = generatePrompt(prompt, aiConfig);
    return queryAi(mapper.writeValueAsString(body));
  }
}
