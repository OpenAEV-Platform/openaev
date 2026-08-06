package io.openaev.ocsf.parser.client.url;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UrlBuilder {
  private static final String OCSF_SCHEMA_HOSTNAME = "schema.ocsf.io";

  private OcsfSchemaEndpoints endpoint;
  private String endpointArgument;
  private final Set<OcsfSchemaExtensions> extensions = new HashSet<>();

  private UrlBuilder() {}

  public static UrlBuilder builder() {
    return new UrlBuilder();
  }

  public UrlBuilder withEndpoint(OcsfSchemaEndpoints endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public UrlBuilder withEndpoint(OcsfSchemaEndpoints endpoint, String argument) {
    this.endpoint = endpoint;
    this.endpointArgument = argument;
    return this;
  }

  public UrlBuilder withExtension(OcsfSchemaExtensions extension) {
    return this.withExtensions(Set.of(extension));
  }

  public UrlBuilder withExtensions(Set<OcsfSchemaExtensions> extensions) {
    this.extensions.addAll(extensions);
    return this;
  }

  public String build() {
    StringBuilder sb = new StringBuilder("https://");
    sb.append(OCSF_SCHEMA_HOSTNAME);
    sb.append(MessageFormat.format(this.endpoint.getValue(), endpointArgument));
    if (!this.extensions.isEmpty()) {
      sb.append("?extensions=");
      sb.append(
          this.extensions.stream()
              .map(OcsfSchemaExtensions::getValue)
              .collect(Collectors.joining(",")));
    }
    return sb.toString();
  }
}
