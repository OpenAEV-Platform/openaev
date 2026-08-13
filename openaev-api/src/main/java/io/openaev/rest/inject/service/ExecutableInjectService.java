package io.openaev.rest.inject.service;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_MANDATORY;
import static io.openaev.database.model.InjectorContract.DEFAULT_VALUE_FIELD;
import static io.openaev.executors.Executor.CMD;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.injectors.openaev.model.OpenAEVImplantInjectContent;
import io.openaev.injectors.openaev.util.OpenAEVObfuscationMap;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.command.CommandArgumentBinder;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private static final Set<String> RESERVED_PLACEHOLDERS = Set.of("location", "payload_location");
  private static final Pattern argumentsRegex = Pattern.compile("#\\{([^#{}]+)}");
  private static final Pattern cmdVariablesRegex = Pattern.compile("%(\\w+)%");

  /**
   * Extracts the argument keys referenced by a command template, in order of appearance and without
   * duplicates — a key referenced twice must only be declared once.
   */
  private Set<String> getArgumentsFromCommandLines(String command) {
    Matcher matcher = argumentsRegex.matcher(command);
    Set<String> commandParameters = new LinkedHashSet<>();

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

  /**
   * Renders a command template by binding every referenced argument to a shell variable instead of
   * substituting its value verbatim.
   *
   * <p>The value is declared once, quoted/escaped by {@link CommandArgumentBinder}, and the
   * template only ever holds a variable reference — so shell metacharacters carried by an argument
   * can no longer alter the structure of the executed command.
   *
   * @param command the command template authored on the payload
   * @param binder the binding strategy matching the target executor
   * @return the command, prefixed with the variable declarations
   */
  private String replaceArgumentsByValue(
      String command,
      CommandArgumentBinder binder,
      List<PayloadArgument> defaultPayloadArguments,
      List<ObjectNode> injectorContractContentFields,
      ObjectNode injectContent,
      boolean enforceMandatory) {

    Set<String> argumentKeys = getArgumentsFromCommandLines(command);
    List<PayloadArgument> payloadArguments =
        defaultPayloadArguments != null ? defaultPayloadArguments : List.of();
    List<ObjectNode> contractFields =
        injectorContractContentFields != null ? injectorContractContentFields : List.of();
    ObjectNode safeInjectContent =
        injectContent != null ? injectContent : mapper.createObjectNode();

    for (String argumentKey : argumentKeys) {
      if (RESERVED_PLACEHOLDERS.contains(argumentKey)) {
        continue;
      }

      PayloadArgument defaultPayloadArgument = findPayloadArgument(argumentKey, payloadArguments);
      boolean mandatory = isMandatoryField(argumentKey, contractFields);
      String defaultValue =
          resolveDefaultValue(argumentKey, defaultPayloadArgument, contractFields);
      ResolvedArgument resolvedArgument =
          resolveArgumentValue(
              argumentKey,
              defaultPayloadArgument,
              defaultValue,
              mandatory,
              contractFields,
              safeInjectContent);

      if (resolvedArgument.missing() && enforceMandatory && resolvedArgument.mandatory()) {
        log.error(
            "[Inject execution] Missing mandatory input '{}' -> step run can not be created/executed",
            argumentKey);
        throw new IllegalStateException(
            "Missing mandatory input '%s' for inject execution".formatted(argumentKey));
      }
      binder.bind(argumentKey, resolvedArgument.value());
    }
    return binder.render(command);
  }

  /**
   * Resolves every {@code #{argumentKey}} placeholder to its actual value for <b>read-only
   * display</b> (e.g. the terminal view), so the UI shows {@code echo localhost:22} rather than the
   * raw template.
   *
   * <p>This performs a plain, verbatim substitution and the result <b>must never be executed</b>:
   * only {@link #replaceArgumentsByValue} with a shell-aware binder is safe for dispatch. The two
   * paths intentionally share the same value-resolution rule ({@link #resolveArgumentValue}) so
   * display and execution stay in sync.
   */
  public String resolveArgumentsForDisplay(
      String command,
      List<PayloadArgument> defaultPayloadArguments,
      List<ObjectNode> injectorContractContentFields,
      ObjectNode injectContent) {
    return replaceArgumentsByValue(
        command,
        CommandArgumentBinder.literal(),
        defaultPayloadArguments,
        injectorContractContentFields,
        injectContent,
        false);
  }

  /** Resolves the effective value of a single argument, before any shell escaping. */
  private ResolvedArgument resolveArgumentValue(
      String argumentKey,
      PayloadArgument defaultPayloadArgument,
      String defaultValue,
      boolean mandatory,
      List<ObjectNode> injectorContractContentFields,
      ObjectNode injectContent) {
    PrimitiveType type = defaultPayloadArgument != null ? defaultPayloadArgument.getType() : null;
    String value = "";
    boolean missing = true;

    // If the argument is a targeted asset, we need to fetch the asset details
    if (PrimitiveType.TargetedAsset == type) {
      value =
          getTargetedAssetArgumentValue(
              argumentKey, injectContent, defaultPayloadArgument, injectorContractContentFields);
      missing = !hasText(value);
    } else if (injectContent.get(argumentKey) != null
        && !injectContent.get(argumentKey).asText().isEmpty()) {
      value = injectContent.get(argumentKey).asText();
      missing = false;
    } else if (hasText(defaultValue)) {
      value = defaultValue;
      missing = false;
    }

    // If arg is a doc, specific handling
    // We need to resolve the doc name and add special prefix #{location} that will be resolved
    // by the implant
    if (PrimitiveType.Document == type && !value.isEmpty()) {
      try {
        Document doc = documentService.document(value);
        value = "#{location}/" + doc.getName();
        missing = false;
      } catch (ElementNotFoundException e) {
        log.error("Payload argument target unexisting document", e);
      }
    }
    return new ResolvedArgument(value, missing, mandatory);
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

  public static String formatMultilineCommand(String command) {
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
    OpenAEVObfuscationMap obfuscationMap = new OpenAEVObfuscationMap(executor);

    String computedCommand = command;
    // cmd-specific rewrites are applied to the TEMPLATE only: running them after substitution would
    // let an argument value smuggle a %VAR% / newline through them.
    if (CMD.equals(executor)) {
      computedCommand = replaceCmdVariables(computedCommand);
      computedCommand = formatMultilineCommand(computedCommand);
    }

    computedCommand =
        replaceArgumentsByValue(
            computedCommand,
            CommandArgumentBinder.forExecutor(executor),
            defaultPayloadArguments,
            injectorContractContentFields,
            injectContent,
            true);

    computedCommand = obfuscationMap.executeObfuscation(obfuscator, computedCommand, executor);

    return Base64.getEncoder().encodeToString(computedCommand.getBytes());
  }

  @Transactional(rollbackFor = Exception.class)
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

    return processPayloadToExecute(
        payloadToExecute, contract, inject, injectorContractFields, obfuscator);
  }

  private Payload processPayloadToExecute(
      Payload payloadToExecute,
      InjectorContract contract,
      Inject inject,
      List<ObjectNode> injectorContractFields,
      String obfuscator) {
    Payload processed =
        switch (contract.getPayload().getTypeEnum()) {
          case PayloadType.COMMAND ->
              processCommandPayload(
                  payloadToExecute, contract, inject, injectorContractFields, obfuscator);
          case PayloadType.DNS_RESOLUTION -> processDnsResolutionPayload(payloadToExecute, inject);
          default ->
              // All other payload types are intentionally passed through unchanged.
              payloadToExecute;
        };
    // Override the default_value of document-type arguments with the actual inject content value
    // for all payload types. The implant uses payload_arguments[].default_value to download
    // documents before execution; without this override it would download the payload's default
    // document instead of the one configured on the inject.
    resolveDocumentArgumentsFromInjectContent(processed, inject.getContent());
    return processed;
  }

  private Payload processCommandPayload(
      Payload payloadToExecute,
      InjectorContract contract,
      Inject inject,
      List<ObjectNode> injectorContractFields,
      String obfuscator) {
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

  private void resolveDocumentArgumentsFromInjectContent(
      Payload payload, ObjectNode injectContent) {
    if (isEmpty(payload.getArguments()) || injectContent == null) {
      return;
    }
    List<PayloadArgument> resolved =
        payload.getArguments().stream()
            .map(
                arg -> {
                  if (PrimitiveType.Document != arg.getType()) {
                    return arg;
                  }
                  String actualValue =
                      getArgumentValueOrDefault(arg.getKey(), injectContent, arg.getDefaultValue());
                  if (!hasText(actualValue) || actualValue.equals(arg.getDefaultValue())) {
                    return arg;
                  }
                  // Create a copy to avoid mutating the shared original PayloadArgument.
                  PayloadArgument copy = new PayloadArgument();
                  copy.setType(arg.getType());
                  copy.setKey(arg.getKey());
                  copy.setDefaultValue(actualValue);
                  copy.setDescription(arg.getDescription());
                  copy.setSeparator(arg.getSeparator());
                  return copy;
                })
            .toList();
    payload.setArguments(new ArrayList<>(resolved));
  }

  private Payload processDnsResolutionPayload(Payload payloadToExecute, Inject inject) {
    DnsResolution dnsResolution = (DnsResolution) payloadToExecute;
    // A hostname is resolved by the implant, not run through a shell: no variable binding applies,
    // the binder only strips control characters.
    dnsResolution.setHostname(
        replaceArgumentsByValue(
            dnsResolution.getHostname(),
            CommandArgumentBinder.literal(),
            dnsResolution.getArguments(),
            null,
            inject.getContent(),
            false));
    return dnsResolution;
  }

  private PayloadArgument findPayloadArgument(
      String argumentKey, List<PayloadArgument> payloadArguments) {
    return payloadArguments.stream()
        .filter(arg -> argumentKey.equals(arg.getKey()))
        .findFirst()
        .orElse(null);
  }

  private ObjectNode findFieldByKey(
      String argumentKey, List<ObjectNode> injectorContractContentFields) {
    return injectorContractContentFields.stream()
        .filter(field -> field.has(CONTRACT_ELEMENT_CONTENT_KEY))
        .filter(field -> argumentKey.equals(field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText()))
        .findFirst()
        .orElse(null);
  }

  private boolean isMandatoryField(
      String argumentKey, List<ObjectNode> injectorContractContentFields) {
    ObjectNode field = findFieldByKey(argumentKey, injectorContractContentFields);
    return field != null
        && field.has(CONTRACT_ELEMENT_CONTENT_MANDATORY)
        && field.get(CONTRACT_ELEMENT_CONTENT_MANDATORY).asBoolean(false);
  }

  private String resolveDefaultValue(
      String argumentKey,
      PayloadArgument defaultPayloadArgument,
      List<ObjectNode> injectorContractContentFields) {
    if (defaultPayloadArgument != null && hasText(defaultPayloadArgument.getDefaultValue())) {
      return defaultPayloadArgument.getDefaultValue();
    }
    ObjectNode field = findFieldByKey(argumentKey, injectorContractContentFields);
    if (field != null && field.has(DEFAULT_VALUE_FIELD)) {
      return field.get(DEFAULT_VALUE_FIELD).asText("");
    }
    return defaultPayloadArgument != null ? defaultPayloadArgument.getDefaultValue() : "";
  }

  private record ResolvedArgument(String value, boolean missing, boolean mandatory) {}
}
