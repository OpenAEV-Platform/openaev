package io.openaev.api.asset.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.asset.software.SbomParser.ParsedComponent;
import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SBOM parser: CycloneDX and SPDX JSON")
class SbomParserTest {

  private final ObjectMapper mapper = new ObjectMapper();

  static final String CYCLONEDX =
      """
      {"bomFormat": "CycloneDX", "specVersion": "1.5", "components": [
        {"bom-ref": "img", "type": "container", "name": "ingress-nginx-controller",
         "group": "kubernetes", "version": "1.15.1",
         "purl": "pkg:docker/ingress-nginx/controller@1.15.1",
         "components": [
           {"bom-ref": "nginx", "type": "application", "name": "nginx", "publisher": "F5",
            "version": "1.27.1", "cpe": "cpe:2.3:a:f5:nginx:1.27.1:*:*:*:*:*:*:*",
            "purl": "pkg:generic/nginx@1.27.1"},
           {"type": "library", "name": "zlib", "version": ""}
         ]}
      ]}
      """;

  static final String SPDX =
      """
      {"spdxVersion": "SPDX-2.3", "SPDXID": "SPDXRef-DOCUMENT", "packages": [
        {"SPDXID": "SPDXRef-nginx", "name": "nginx", "versionInfo": "1.27.1",
         "supplier": "Organization: F5, Inc.",
         "externalRefs": [
           {"referenceCategory": "SECURITY", "referenceType": "cpe23Type",
            "referenceLocator": "cpe:2.3:a:f5:nginx:1.27.1:*:*:*:*:*:*:*"},
           {"referenceCategory": "PACKAGE-MANAGER", "referenceType": "purl",
            "referenceLocator": "pkg:generic/nginx@1.27.1"}]},
        {"SPDXID": "SPDXRef-image", "name": "web-image", "versionInfo": "NOASSERTION",
         "supplier": "NOASSERTION"}
      ],
      "relationships": [
        {"spdxElementId": "SPDXRef-DOCUMENT", "relationshipType": "DESCRIBES",
         "relatedSpdxElement": "SPDXRef-image"},
        {"spdxElementId": "SPDXRef-image", "relationshipType": "CONTAINS",
         "relatedSpdxElement": "SPDXRef-nginx"}
      ]}
      """;

  @Test
  @DisplayName("given a CycloneDX SBOM, should read nested components with their parent")
  void given_cycloneDx_should_readNestedComponents() throws Exception {
    List<ParsedComponent> components = SbomParser.parse(mapper.readTree(CYCLONEDX));

    assertThat(components).hasSize(3);
    ParsedComponent image = components.get(0);
    assertThat(image.parentRef()).isNull();
    assertThat(image.vendor()).isEqualTo("kubernetes");
    ParsedComponent nginx = components.get(1);
    assertThat(nginx.parentRef()).isEqualTo("img");
    assertThat(nginx.vendor()).isEqualTo("F5");
    assertThat(nginx.version()).isEqualTo("1.27.1");
    assertThat(nginx.cpe()).startsWith("cpe:2.3:a:f5:nginx:1.27.1");
    assertThat(nginx.purl()).isEqualTo("pkg:generic/nginx@1.27.1");
    ParsedComponent zlib = components.get(2);
    assertThat(zlib.parentRef()).isEqualTo("img");
    assertThat(zlib.version()).as("a blank version is unknown, never empty").isNull();
  }

  @Test
  @DisplayName("given an SPDX SBOM, should read packages, references and CONTAINS parents")
  void given_spdx_should_readPackagesAndParents() throws Exception {
    List<ParsedComponent> components = SbomParser.parse(mapper.readTree(SPDX));

    assertThat(components).hasSize(2);
    ParsedComponent nginx = components.get(0);
    assertThat(nginx.parentRef()).isEqualTo("SPDXRef-image");
    assertThat(nginx.vendor()).isEqualTo("F5, Inc.");
    assertThat(nginx.cpe()).startsWith("cpe:2.3:a:f5:nginx");
    assertThat(nginx.purl()).isEqualTo("pkg:generic/nginx@1.27.1");
    ParsedComponent image = components.get(1);
    assertThat(image.parentRef()).isNull();
    assertThat(image.version()).as("NOASSERTION is an unknown version").isNull();
    assertThat(image.vendor()).isNull();
  }

  @Test
  @DisplayName("given a document that is neither format, should refuse it")
  void given_unknownDocument_should_refuse() throws Exception {
    JsonNode other = mapper.readTree("{\"name\": \"not an sbom\"}");
    assertThatThrownBy(() -> SbomParser.parse(other)).isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> SbomParser.parse(mapper.readTree("[]")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("identity: PURL first, then CPE, then vendor, product and version")
  void identity_prefersPurlThenCpe() {
    assertThat(SoftwareIdentity.key("F5", "nginx", "1.27.1", "cpe:X", "pkg:generic/nginx@1.27.1"))
        .isEqualTo("purl:pkg:generic/nginx@1.27.1");
    assertThat(SoftwareIdentity.key("F5", "nginx", "1.27.1", "CPE:X", null)).isEqualTo("cpe:cpe:x");
    assertThat(SoftwareIdentity.key("F5", "NGINX", null, null, null)).isEqualTo("name:f5|nginx|?");
    assertThat(SoftwareIdentity.purlHasVersion("pkg:generic/nginx@1.27.1")).isTrue();
    assertThat(SoftwareIdentity.purlHasVersion("pkg:npm/%40scope/name")).isFalse();
    assertThat(SoftwareIdentity.escapeLike("a_b%c!")).isEqualTo("a!_b!%c!!");
  }
}
