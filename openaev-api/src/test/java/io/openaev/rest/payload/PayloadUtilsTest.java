package io.openaev.rest.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.PrimitiveType;
import io.openaev.rest.payload.form.PayloadCreateInput;
import org.junit.jupiter.api.Test;

class PayloadUtilsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void given_legacyComplexArgumentWithSubtype_should_throw() throws Exception {
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
                {"type":"portscan","subtype":"host","key":"target","default_value":"srv-01","description":"d"}
              ]
            }
            """);

    assertThrows(IllegalArgumentException.class, () -> PayloadUtils.buildPayload(payloadNode));
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
