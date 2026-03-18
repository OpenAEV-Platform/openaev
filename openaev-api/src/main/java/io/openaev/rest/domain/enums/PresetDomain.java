package io.openaev.rest.domain.enums;

import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import java.time.Instant;
import java.util.*;

public class PresetDomain {
  public static Domain ENDPOINT = new Domain(null, "Endpoint", "#389CFF", Instant.now(), null);
  public static Domain NETWORK = new Domain(null, "Network", "#009933", Instant.now(), null);
  public static Domain WEB_APP = new Domain(null, "Web App", "#FF9933", Instant.now(), null);
  public static Domain EMAIL_INFILTRATION =
      new Domain(null, "E-mail Infiltration", "#FF6666", Instant.now(), null);
  public static Domain DATA_EXFILTRATION =
      new Domain(null, "Data Exfiltration", "#9933CC", Instant.now(), null);
  public static Domain URL_FILTERING =
      new Domain(null, "URL Filtering", "#66CCFF", Instant.now(), null);
  public static Domain CLOUD = new Domain(null, "Cloud", "#9999CC", Instant.now(), null);
  public static Domain TABLETOP = new Domain(null, "Tabletop", "#FFCC33", Instant.now(), null);
  public static Domain TOCLASSIFY = new Domain(null, "To classify", "#FFFFFF", Instant.now(), null);

  private static final Map<Domain, List<String>> domainKeywordsMap =
      Map.of(
          NETWORK, List.of("network", "ftp", "smb", "llmnr", "nmap"),
          WEB_APP, List.of("web"),
          EMAIL_INFILTRATION, List.of("mail", "phishing"),
          DATA_EXFILTRATION, List.of("exfiltrat"),
          URL_FILTERING, List.of("bitsadmin"),
          CLOUD, List.of("aws", "azure", "gcp"));

  public static List<Domain> getDomainsForTenant(Tenant tenant) {
    List<Domain> domains = new ArrayList<>();
    domains.add(new Domain(ENDPOINT.getName(), ENDPOINT.getColor(), tenant));
    domains.add(new Domain(NETWORK.getName(), NETWORK.getColor(), tenant));
    domains.add(new Domain(WEB_APP.getName(), WEB_APP.getColor(), tenant));
    return domains;
  }

  public static Set<Domain> getRelevantDomainsFromKeywords(String searchValue) {
    Set<Domain> domains = new HashSet<>();
    domainKeywordsMap.forEach(
        (domain, keywords) -> {
          if (foundInKeywords(keywords, searchValue)) {
            domains.add(domain);
          }
        });
    return domains;
  }

  private static boolean foundInKeywords(List<String> keywords, String searchValue) {
    return keywords.stream()
        .map(String::toLowerCase)
        .anyMatch(keyword -> searchValue.toLowerCase().contains(keyword));
  }
}
