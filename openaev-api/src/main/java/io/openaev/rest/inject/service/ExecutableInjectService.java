package io.openaev.rest.inject.service;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.injectors.openaev.model.OpenAEVImplantInjectContent;
import io.openaev.injectors.openaev.util.OpenAEVObfuscationMap;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.InjectExpectationService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutableInjectService {

  private final InjectService injectService;
  private final DocumentService documentService;
  private final InjectStatusService injectStatusService;
  private final InjectExpectationService injectExpectationService;
  private final PayloadService payloadService;

  @Resource protected ObjectMapper mapper;

  private static final Pattern argumentsRegex = Pattern.compile("#\\{([^#{}]+)}");
  private static final Pattern cmdVariablesRegex = Pattern.compile("%(\\w+)%");

  private List<String> getArgumentsFromCommandLines(String command) {
    Matcher matcher = argumentsRegex.matcher(command);
    List<String> commandParameters = new ArrayList<>();

    while (matcher.find()) {
      commandParameters.add(matcher.group(1));
    }

    return commandParameters;
  }

  private String getArgumentValueOrDefault(
      String key, ObjectNode injectContent, String defaultValue) {
    return injectContent.get(key) != null && !injectContent.get(key).asText().isEmpty()
        ? injectContent.get(key).asText()
        : defaultValue;
  }

  private String getTargetedAssetArgumentValue(
      String argumentKey,
      ObjectNode injectContent,
      PayloadArgument defaultPayloadArgument,
      List<ObjectNode> injectorContractContentFields) {
    Map<String, Endpoint> valuesAssetsMap =
        injectService.retrieveValuesOfTargetedAssetFromInject(
            injectorContractContentFields, injectContent, argumentKey);

    String assetSeparator =
        getArgumentValueOrDefault(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-" + argumentKey,
            injectContent,
            defaultPayloadArgument.getSeparator());

    return String.join(assetSeparator, valuesAssetsMap.keySet());
  }

  private String replaceArgumentsByValue(
      String command,
      List<PayloadArgument> defaultPayloadArguments,
      List<ObjectNode> injectorContractContentFields,
      ObjectNode injectContent) {

    List<String> argumentKeys = getArgumentsFromCommandLines(command);

    for (String argumentKey : argumentKeys) {
      String value;
      PayloadArgument defaultPayloadArgument =
          defaultPayloadArguments.stream()
              .filter(a -> a.getKey().equals(argumentKey))
              .findFirst()
              .orElse(null);

      // If the argument is a targeted asset, we need to fetch the asset details
      if (defaultPayloadArgument != null
          && ContractFieldType.TargetedAsset.label.equals(defaultPayloadArgument.getType())) {
        value =
            getTargetedAssetArgumentValue(
                argumentKey, injectContent, defaultPayloadArgument, injectorContractContentFields);

      } else {
        value =
            getArgumentValueOrDefault(
                argumentKey,
                injectContent,
                defaultPayloadArgument != null ? defaultPayloadArgument.getDefaultValue() : "");
        // If arg is a doc, specific handling
        // We need to resolve the doc name and add special prefix #{location} that will be resolved
        // by the implant
        boolean isDocArg =
            defaultPayloadArgument != null
                && defaultPayloadArgument.getType().equalsIgnoreCase("document");
        if (isDocArg && !value.isEmpty()) {
          try {
            Document doc = documentService.document(value);
            value = "#{location}/" + doc.getName();
          } catch (ElementNotFoundException e) {
            log.error("Payload argument target unexisting document", e);
          }
        }
      }

      command = command.replace("#{" + argumentKey + "}", value);
    }
    return command;
  }

  public static String replaceCmdVariables(String cmd) {
    Matcher matcher = cmdVariablesRegex.matcher(cmd);

    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String variableName = matcher.group(1);
      matcher.appendReplacement(result, "!" + variableName + "!");
    }
    matcher.appendTail(result);

    return result.toString();
  }

  private static String formatMultilineCommand(String command) {
    String[] lines = command.split("\n");
    StringBuilder formattedCommand = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmedLine = line.trim();
      if (trimmedLine.isEmpty()) {
        continue;
      }
      formattedCommand.append(trimmedLine);

      boolean isLastLine = (i == lines.length - 1);
      boolean isAfterParentheses = trimmedLine.endsWith("(");
      boolean isBeforeParentheses = !isLastLine && lines[i + 1].trim().startsWith(")");

      if (!isAfterParentheses && !isBeforeParentheses && !isLastLine) {
        formattedCommand.append(" & ");
      } else {
        formattedCommand.append(" ");
      }
    }

    return formattedCommand.toString();
  }

  private String processAndEncodeCommand(
      String command,
      String executor,
      List<PayloadArgument> defaultPayloadArguments,
      ObjectNode injectContent,
      List<ObjectNode> injectorContractContentFields,
      String obfuscator) {
    OpenAEVObfuscationMap obfuscationMap = new OpenAEVObfuscationMap();
    String computedCommand =
        replaceArgumentsByValue(
            command, defaultPayloadArguments, injectorContractContentFields, injectContent);

    if (executor.equals("cmd")) {
      computedCommand = replaceCmdVariables(computedCommand);
      computedCommand = formatMultilineCommand(computedCommand);
    }

    computedCommand = obfuscationMap.executeObfuscation(obfuscator, computedCommand, executor);

    return Base64.getEncoder().encodeToString(computedCommand.getBytes());
  }

  public Payload getExecutablePayloadAndUpdateInjectStatus(String injectId, String agentId)
      throws Exception {
    // Need startTime to be defined before everything else to be the most accurate start time, as
    // this whole process is
    // called at the beginning of the implant execution. A better solution would be to have the
    // implant send the start time
    // but it would require more changes in the implant code and change this endpoint from a get to
    // a post.
    Instant startTime = Instant.now();
    Payload payloadToExecute = getExecutablePayloadInject(injectId);
    this.injectStatusService.addStartImplantExecutionTraceByInject(
        injectId, agentId, "Implant is up and starting execution", startTime);
    this.injectExpectationService.addStartDateSignatureToInjectExpectationsByAgent(
        injectId, agentId, startTime);
    return payloadToExecute;
  }

  private Payload getExecutablePayloadInject(String injectId) throws Exception {
    Inject inject = injectService.inject(injectId);
    InjectorContract contract =
        inject
            .getInjectorContract()
            .orElseThrow(() -> new ElementNotFoundException("Inject contract not found"));
    OpenAEVImplantInjectContent content =
        injectService.convertInjectContent(inject, OpenAEVImplantInjectContent.class);
    String obfuscator = content.getObfuscator() != null ? content.getObfuscator() : "plain-text";

    if (contract.getPayload() == null) {
      throw new ElementNotFoundException("Payload not found");
    }
    Payload payloadToExecute = payloadService.generateDuplicatedPayload(contract.getPayload());
    JsonNode injectorContractFieldsNode = contract.getConvertedContent().get("fields");
    List<ObjectNode> injectorContractFields =
        StreamSupport.stream(injectorContractFieldsNode.spliterator(), false)
            .map(ObjectNode.class::cast)
            .toList();

    // prerequisite
    if (!isEmpty(contract.getPayload().getPrerequisites())) {
      List<PayloadPrerequisite> prerequisiteList = new ArrayList<>();
      contract
          .getPayload()
          .getPrerequisites()
          .forEach(
              prerequisite -> {
                PayloadPrerequisite payload = new PayloadPrerequisite();
                payload.setExecutor(prerequisite.getExecutor());
                if (hasText(prerequisite.getCheckCommand())) {
                  payload.setCheckCommand(
                      processAndEncodeCommand(
                          prerequisite.getCheckCommand(),
                          prerequisite.getExecutor(),
                          contract.getPayload().getArguments(),
                          inject.getContent(),
                          injectorContractFields,
                          obfuscator));
                }
                if (hasText(prerequisite.getGetCommand())) {
                  payload.setGetCommand(
                      processAndEncodeCommand(
                          prerequisite.getGetCommand(),
                          prerequisite.getExecutor(),
                          contract.getPayload().getArguments(),
                          inject.getContent(),
                          injectorContractFields,
                          obfuscator));
                }
                prerequisiteList.add(payload);
              });
      payloadToExecute.setPrerequisites(prerequisiteList);
    }

    // cleanup
    if (contract.getPayload().getCleanupCommand() != null) {
      payloadToExecute.setCleanupExecutor(contract.getPayload().getCleanupExecutor());
      payloadToExecute.setCleanupCommand(
          processAndEncodeCommand(
              contract.getPayload().getCleanupCommand(),
              contract.getPayload().getCleanupExecutor(),
              contract.getPayload().getArguments(),
              inject.getContent(),
              injectorContractFields,
              obfuscator));
    }

    // Command
    if (contract.getPayload().getTypeEnum().equals(PayloadType.COMMAND)) {
      Command payloadCommand = (Command) payloadToExecute;
      payloadCommand.setExecutor(((Command) contract.getPayload()).getExecutor());
      payloadCommand.setContent(
          processAndEncodeCommand(
              payloadCommand.getContent(),
              payloadCommand.getExecutor(),
              contract.getPayload().getArguments(),
              inject.getContent(),
              injectorContractFields,
              obfuscator));
      return payloadCommand;
    }

    return payloadToExecute;
  }
}
