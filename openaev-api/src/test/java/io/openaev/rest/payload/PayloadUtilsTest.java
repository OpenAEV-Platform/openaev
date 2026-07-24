package io.openaev.rest.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.database.model.PrimitiveType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.payload.output_parser.OutputParserService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayloadUtilsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void given_legacyComplexArgument_should_mapToReplacementPrimitive() throws Exception {
    JsonNode payloadNode =
        mapper.readTree(
            """
            {
              "payload_type":"command",
              "payload_name":"legacy-payload",
              "payload_source":"MANUAL",
              "payload_status":"VERIFIED",
              "payload_platforms":["Linux"],
              "payload_arguments":[
                {"type":"portscan","subtype":"host","key":"target","default_value":"srv-01","description":"d"},
                {"type":"credentials","key":"client_id","default_value":"??","description":"d"}
              ]
            }
            """);

    PayloadCreateInput payload = PayloadUtils.buildPayload(payloadNode);

    assertEquals(PrimitiveType.Port, payload.getArguments().get(0).getType());
    assertEquals(PrimitiveType.Username, payload.getArguments().get(1).getType());
  }

  @Test
  void given_unknownArgumentType_should_throw() throws Exception {
    JsonNode payloadNode =
        mapper.readTree(
            """
            {
              "payload_type":"command",
              "payload_name":"bad-payload",
              "payload_source":"MANUAL",
              "payload_status":"VERIFIED",
              "payload_platforms":["Linux"],
              "payload_arguments":[
                {"type":"definitely-not-a-type","key":"target","default_value":"x","description":"d"}
              ]
            }
            """);

    assertThrows(IllegalArgumentException.class, () -> PayloadUtils.buildPayload(payloadNode));
  }

  @Test
  void given_legacyArgumentTypeInStoredJson_should_deserializeThroughJackson() throws Exception {
    // Mirrors the hypersistence JsonType read path on pre-#6536 rows (e.g. the NetExec payload):
    // Jackson must accept legacy ArgumentType labels, otherwise the entity row is unreadable.
    String storedColumn =
        """
        [{"key": "client_id", "type": "credentials", "separator": null, "description": null, "default_value": "??"},
         {"key": "ip", "type": "targeted-asset", "separator": ",", "description": null, "default_value": "local_ip"}]
        """;

    List<PayloadArgument> arguments =
        mapper.readValue(storedColumn, new TypeReference<List<PayloadArgument>>() {});

    assertEquals(PrimitiveType.Username, arguments.get(0).getType());
    assertEquals(PrimitiveType.TargetedAsset, arguments.get(1).getType());
  }

  @Test
  void given_duplicatedArgumentsAndPrerequisites_should_dedupeOnUpsertCopy() {
    // Regression: the Atomic Red Team collector used to send the same argument key several
    // times (once per occurrence of the path in command/cleanup/prerequisites), producing
    // duplicated fields in the generated injector contract.
    PayloadUtils payloadUtils =
        new PayloadUtils(
            mock(EnterpriseEditionService.class),
            mock(LicenseCacheManager.class),
            mock(OutputParserService.class),
            mock(DetectionRemediationUtils.class));

    PayloadUpsertInput input = new PayloadUpsertInput();
    input.setArguments(
        List.of(
            argument("AdFind_exe_atomicredteam_path", "./ExternalPayloads/AdFind.exe"),
            argument("AdFind_exe_atomicredteam_path", "./ExternalPayloads/AdFind.exe"),
            argument("optional_args", "test"),
            argument("AdFind_exe_atomicredteam_path", "./ExternalPayloads/other/AdFind.exe")));
    PayloadPrerequisite prerequisite = new PayloadPrerequisite();
    prerequisite.setExecutor("psh");
    prerequisite.setGetCommand("Invoke-WebRequest ...");
    prerequisite.setCheckCommand("if (Test-Path ...) {exit 0} else {exit 1}");
    PayloadPrerequisite prerequisiteDuplicate = new PayloadPrerequisite();
    prerequisiteDuplicate.setExecutor("psh");
    prerequisiteDuplicate.setGetCommand("Invoke-WebRequest ...");
    prerequisiteDuplicate.setCheckCommand("if (Test-Path ...) {exit 0} else {exit 1}");
    input.setPrerequisites(List.of(prerequisite, prerequisiteDuplicate));

    Payload target = payloadUtils.copyProperties(input, new Payload(), false);

    assertEquals(
        List.of("AdFind_exe_atomicredteam_path", "optional_args"),
        target.getArguments().stream().map(PayloadArgument::getKey).toList());
    // First occurrence wins
    assertEquals(
        "./ExternalPayloads/AdFind.exe", target.getArguments().getFirst().getDefaultValue());
    assertEquals(1, target.getPrerequisites().size());
  }

  private static PayloadArgument argument(String key, String defaultValue) {
    PayloadArgument argument = new PayloadArgument();
    argument.setType(PrimitiveType.Text);
    argument.setKey(key);
    argument.setDefaultValue(defaultValue);
    return argument;
  }

  @Test
  void given_primitiveArgument_should_keepPrimitiveType() throws Exception {
    JsonNode payloadNode =
        mapper.readTree(
            """
            {
              "payload_type":"command",
              "payload_name":"primitive-payload",
              "payload_source":"MANUAL",
              "payload_status":"VERIFIED",
              "payload_platforms":["Linux"],
              "payload_arguments":[
                {"type":"targeted-asset","key":"target","default_value":"asset-01","description":"d"}
              ]
            }
            """);

    PayloadCreateInput payload = PayloadUtils.buildPayload(payloadNode);

    assertEquals(PrimitiveType.TargetedAsset, payload.getArguments().getFirst().getType());
  }
}
