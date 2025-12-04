package io.openaev.rest.domain.enums;

import io.openaev.database.model.Domain;
import java.time.Instant;
import lombok.Getter;

@Getter
public enum DefaultDomain {
  ENDPOINT(new Domain(null, "Endpoint", "#389CFF", Instant.now(), null)),
  NETWORK(new Domain(null, "Network", "#009933", Instant.now(), null)),
  WEB_APP(new Domain(null, "Web App", "#FF9933", Instant.now(), null)),
  EMAIL_INFILTRATION(new Domain(null, "E-mail Infiltration", "#FF6666", Instant.now(), null)),
  DATA_EXFILTRATION(new Domain(null, "Data Exfiltration", "#9933CC", Instant.now(), null)),
  URL_FILTERING(new Domain(null, "URL Filtering", "#66CCFF", Instant.now(), null)),
  CLOUD(new Domain(null, "Cloud", "#9999CC", Instant.now(), null)),
  TABLE_TOP(new Domain(null, "Table-Top", "#FFCC33", Instant.now(), null)),
  TOCLASSIFY(new Domain(null, "To classify", "#FFFFFF", Instant.now(), null));

  private final Domain domain;

  DefaultDomain(Domain domain) {
    this.domain = domain;
  }
}
