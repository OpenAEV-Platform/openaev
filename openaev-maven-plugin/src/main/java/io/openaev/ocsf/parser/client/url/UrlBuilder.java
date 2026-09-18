package io.openaev.ocsf.parser.client.url;

import io.openaev.ocsf.parser.schema.Version;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UrlBuilder {
  private static final String OCSF_SCHEMA_HOSTNAME = "schema.ocsf.io";

  private OcsfSchemaEndpoints endpoint;
  private String endpointArgument;
  private final Set<OcsfSchemaExtension> extensions = new HashSet<>();
  private Version version;

  private UrlBuilder() {}

  public static UrlBuilder builder() {
    return new UrlBuilder();
  }

  public UrlBuilder withVersion(Version version) {
    this.version = version;
    return this;
  }

  public UrlBuilder withEndpoint(OcsfSchemaEndpoints endpoint, String argument) {
    this.endpoint = endpoint;
    this.endpointArgument = argument;
    return this;
  }

  public UrlBuilder withExtensions(Set<OcsfSchemaExtension> extensions) {
    this.extensions.addAll(extensions);
    return this;
  }

  public String build() {
    StringBuilder sb = new StringBuilder("https://");
    sb.append(OCSF_SCHEMA_HOSTNAME);
    if (this.version != null) {
      sb.append("/").append(this.version.versionNumber().getValue());
    }
    sb.append(MessageFormat.format(this.endpoint.getValue(), endpointArgument));
    if (!this.extensions.isEmpty()) {
      sb.append("?extensions=");
      sb.append(
          this.extensions.stream()
              .map(OcsfSchemaExtension::getValue)
              .collect(Collectors.joining(",")));
    }
    return sb.toString();
  }
}
