package io.openaev.stix.parsing;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.constants.ExtendedProperties;
import io.openaev.stix.types.Hashes;
import io.openaev.stix.types.enums.HashingAlgorithms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ParserTest {

  @Nested
  @DisplayName("Hashes type parsing")
  public class HashesParsingTest {
    @Test
    @DisplayName("Given an OpenCTI extension object containing hashes, can parse correctly")
    public void given_openCTIExtensionObjectContainingHashes_then_canParseCorrectly()
        throws ParsingException, JsonProcessingException {
      // based on an actual use case from OpenCTI
      String indicatorStixString =
          """
        {
          "id" : "indicator--cee5de91-15a3-5554-a7f6-b9bddfd8861d",
          "spec_version" : "2.1",
          "type" : "indicator",
          "extensions" : {
            "extension-definition--ea279b3e-5c71-4632-ac08-831c66a786ba" : {
              "extension_type" : "property-extension",
              "id" : "c8e73613-d2f7-43bd-87a1-ab3040ef2052",
              "type" : "Indicator",
              "created_at" : "2026-03-31T10:04:45.090Z",
              "updated_at" : "2026-03-31T13:30:33.926Z",
              "files" : [ {
                "name" : "some_image.png",
                "uri" : "/storage/get/import/Indicator/c8e73613-d2f7-43bd-87a1-ab3040ef2052/some_image.png",
                "version" : "2026-03-31T10:04:45.092Z",
                "mime_type" : "image/png",
                "object_marking_refs" : [ ]
              } ],
              "is_inferred" : false,
              "creator_ids" : [ "88ec0c6a-13ce-5e39-b486-354fe4a7084f" ],
              "detection" : false,
              "score" : 50,
              "main_observable_type" : "StixFile",
              "observable_values" : [ {
                "type" : "StixFile",
                "hashes" : {
                  "md5" : "ee31a957e35f826bbde01c2824c6dc7a"
                }
              }, {
                "type" : "StixFile",
                "hashes" : {
                  "MD5" : "ee31a957e35f826bbde01c2824c6dc7a"
                }
              }, {
                "type" : "StixFile",
                "hashes" : {
                  "sha1" : "540efc63ae2618c94b5bf93da80a1eda934bcdbb"
                }
              }, {
                "type" : "StixFile",
                "hashes" : {
                  "SHA-1" : "540efc63ae2618c94b5bf93da80a1eda934bcdbb"
                }
              }, {
                "type" : "StixFile",
                "hashes" : {
                  "sha256" : "6c93c0a227c0f8631a761ac0f83c79674550c80964a116cd92a3ab8e4cff8bc6"
                }
              }, {
                "type" : "StixFile",
                "hashes" : {
                  "SHA-256" : "6c93c0a227c0f8631a761ac0f83c79674550c80964a116cd92a3ab8e4cff8bc6"
                }
              } ]
            },
            "extension-definition--322b8f77-262a-4cb8-a915-1e441e00329b" : {
              "extension_type" : "property-extension"
            }
          },
          "created" : "2026-03-31T10:04:45.090Z",
          "modified" : "2026-03-31T13:30:33.926Z",
          "revoked" : false,
          "confidence" : 100,
          "lang" : "en",
          "name" : "md5?",
          "indicator_types" : [ "compromised" ],
          "pattern" : "[file:hashes.md5 = 'ee31a957e35f826bbde01c2824c6dc7a' OR file:hashes.MD5 = 'ee31a957e35f826bbde01c2824c6dc7a' OR file:hashes.sha1 = '540efc63ae2618c94b5bf93da80a1eda934bcdbb' OR file:hashes.'SHA-1' = '540efc63ae2618c94b5bf93da80a1eda934bcdbb' OR file:hashes.sha256 = '6c93c0a227c0f8631a761ac0f83c79674550c80964a116cd92a3ab8e4cff8bc6' OR file:hashes.'SHA-256' = '6c93c0a227c0f8631a761ac0f83c79674550c80964a116cd92a3ab8e4cff8bc6']",
          "pattern_type" : "stix",
          "valid_from" : "2026-03-31T10:04:44.975Z",
          "valid_until" : "2027-01-15T21:14:29.532Z"
        }
      """;

      Parser parser = new Parser(new ObjectMapper());
      ObjectBase sdo = parser.parseObject(indicatorStixString);

      assertThat(
              ((Hashes)
                      sdo.getExtensionObservables(ExtendedProperties.OPENCTI_EXTENSION_DEFINITION)
                          .get(0)
                          .get("hashes"))
                  .get(HashingAlgorithms.MD5))
          .isEqualTo("ee31a957e35f826bbde01c2824c6dc7a");
    }

    @Test
    @DisplayName("Given known hash algorithm strings, parse correctly")
    public void given_hashAlgorithms_then_parseCorrectly()
        throws ParsingException, JsonProcessingException {
      String hashesString =
          """
        {
          "id" : "object--35be2e39-7e55-4feb-8a77-88dfe5a2341b",
          "spec_version" : "2.1",
          "type" : "object",
          "hashes" : {
            "md5" : "8532054bba77e4d26caca1b1299cdb0c",
            "MD5" : "8532054bba77e4d26caca1b1299cdb0c",
            "sha1" : "102554c32867b1f4be152f558a92d592141b2633",
            "SHA1" : "102554c32867b1f4be152f558a92d592141b2633",
            "sha-1" : "102554c32867b1f4be152f558a92d592141b2633",
            "SHA-1" : "102554c32867b1f4be152f558a92d592141b2633",
            "sha256" : "c74b2e30852573d52b97733529d072553c74fe354f6ebf4ada5f609be029aa38",
            "SHA256" : "c74b2e30852573d52b97733529d072553c74fe354f6ebf4ada5f609be029aa38",
            "sha-256" : "c74b2e30852573d52b97733529d072553c74fe354f6ebf4ada5f609be029aa38",
            "SHA-256" : "c74b2e30852573d52b97733529d072553c74fe354f6ebf4ada5f609be029aa38",
            "sha512" : "f99385fa7584982fe8c9a99f3c995bf34709ae7f3301cef3a394f9251e5aae58df7f5856303d623de2ebacc6796cbe6414161d8948fd1fcb95e807397cc9959e",
            "SHA512" : "f99385fa7584982fe8c9a99f3c995bf34709ae7f3301cef3a394f9251e5aae58df7f5856303d623de2ebacc6796cbe6414161d8948fd1fcb95e807397cc9959e",
            "sha-512" : "f99385fa7584982fe8c9a99f3c995bf34709ae7f3301cef3a394f9251e5aae58df7f5856303d623de2ebacc6796cbe6414161d8948fd1fcb95e807397cc9959e",
            "SHA-512" : "f99385fa7584982fe8c9a99f3c995bf34709ae7f3301cef3a394f9251e5aae58df7f5856303d623de2ebacc6796cbe6414161d8948fd1fcb95e807397cc9959e",
            "sha3256" : "7e46df2a110325d855d4e118dcee65423e46e0295092b04b69183e20fba24dbf",
            "SHA3256" : "7e46df2a110325d855d4e118dcee65423e46e0295092b04b69183e20fba24dbf",
            "sha3-256" : "7e46df2a110325d855d4e118dcee65423e46e0295092b04b69183e20fba24dbf",
            "SHA3-256" : "7e46df2a110325d855d4e118dcee65423e46e0295092b04b69183e20fba24dbf",
            "sha3512" : "d672faa84fb894fb6e165ef7cf9ce43706d4e5265f80665b66d52a5d35c428befe1e551d7f6dd11fb5509bfaacb6edcc461ecae4db23fc14108830043de552c2",
            "SHA3512" : "d672faa84fb894fb6e165ef7cf9ce43706d4e5265f80665b66d52a5d35c428befe1e551d7f6dd11fb5509bfaacb6edcc461ecae4db23fc14108830043de552c2",
            "sha3-512" : "d672faa84fb894fb6e165ef7cf9ce43706d4e5265f80665b66d52a5d35c428befe1e551d7f6dd11fb5509bfaacb6edcc461ecae4db23fc14108830043de552c2",
            "SHA3-512" : "d672faa84fb894fb6e165ef7cf9ce43706d4e5265f80665b66d52a5d35c428befe1e551d7f6dd11fb5509bfaacb6edcc461ecae4db23fc14108830043de552c2",
            "ssdeep" : "ssdeep,1.1--blocksize:hash:hash,filename 3:oEvn:oEvn,stdin",
            "SSDEEP" : "ssdeep,1.1--blocksize:hash:hash,filename 3:oEvn:oEvn,stdin",
            "tlsh" : "2FD1B6ABD2341B25174303E1E29760F4E73380DD5EA3D85F912DD0A8712897A933F695",
            "TLSH" : "2FD1B6ABD2341B25174303E1E29760F4E73380DD5EA3D85F912DD0A8712897A933F695"
          }
        }
      """;

      Parser parser = new Parser(new ObjectMapper());
      ObjectBase sdo = parser.parseObject(hashesString);

      Hashes hashes = (Hashes) sdo.getProperty("hashes");

      assertThat(hashes.get(HashingAlgorithms.MD5)).isEqualTo("8532054bba77e4d26caca1b1299cdb0c");
      assertThat(hashes.get(HashingAlgorithms.SHA1))
          .isEqualTo("102554c32867b1f4be152f558a92d592141b2633");
      assertThat(hashes.get(HashingAlgorithms.SHA256))
          .isEqualTo("c74b2e30852573d52b97733529d072553c74fe354f6ebf4ada5f609be029aa38");
      assertThat(hashes.get(HashingAlgorithms.SHA512))
          .isEqualTo(
              "f99385fa7584982fe8c9a99f3c995bf34709ae7f3301cef3a394f9251e5aae58df7f5856303d623de2ebacc6796cbe6414161d8948fd1fcb95e807397cc9959e");
      assertThat(hashes.get(HashingAlgorithms.SHA3256))
          .isEqualTo("7e46df2a110325d855d4e118dcee65423e46e0295092b04b69183e20fba24dbf");
      assertThat(hashes.get(HashingAlgorithms.SHA3512))
          .isEqualTo(
              "d672faa84fb894fb6e165ef7cf9ce43706d4e5265f80665b66d52a5d35c428befe1e551d7f6dd11fb5509bfaacb6edcc461ecae4db23fc14108830043de552c2");
      assertThat(hashes.get(HashingAlgorithms.SSDEEP))
          .isEqualTo("ssdeep,1.1--blocksize:hash:hash,filename 3:oEvn:oEvn,stdin");
      assertThat(hashes.get(HashingAlgorithms.TLSH))
          .isEqualTo("2FD1B6ABD2341B25174303E1E29760F4E73380DD5EA3D85F912DD0A8712897A933F695");
    }

    @Test
    @DisplayName("Given unknown hash algorithm strings, throw")
    public void given_unknownHashAlgorithms_then_throw()
        throws ParsingException, JsonProcessingException {
      String hashesString =
          """
        {
          "id" : "object--35be2e39-7e55-4feb-8a77-88dfe5a2341b",
          "spec_version" : "2.1",
          "type" : "object",
          "hashes" : {
            "whatisit" : "this is not a known hash algo"
          }
        }
      """;

      Parser parser = new Parser(new ObjectMapper());
      assertThatThrownBy(() -> parser.parseObject(hashesString))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
