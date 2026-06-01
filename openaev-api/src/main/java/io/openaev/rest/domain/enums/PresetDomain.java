package io.openaev.rest.domain.enums;

import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import java.util.*;

public class PresetDomain {
  private static final Domain ENDPOINT = Domain.builder().name("Endpoint").color("#389CFF").build();
  private static final Domain NETWORK = Domain.builder().name("Network").color("#009933").build();
  private static final Domain WEB_APP = Domain.builder().name("Web App").color("#FF9933").build();
  private static final Domain EMAIL_INFILTRATION =
      Domain.builder().name("E-mail Infiltration").color("#FF6666").build();
  private static final Domain DATA_EXFILTRATION =
      Domain.builder().name("Data Exfiltration").color("#9933CC").build();
  private static final Domain URL_FILTERING =
      Domain.builder().name("URL Filtering").color("#66CCFF").build();
  private static final Domain CLOUD = Domain.builder().name("Cloud").color("#9999CC").build();
  private static final Domain TABLETOP = Domain.builder().name("Tabletop").color("#FFCC33").build();
  private static final Domain TOCLASSIFY =
      Domain.builder().name("To classify").color("#FFFFFF").build();

  private static final Map<Domain, List<String>> domainKeywordsMap =
      Map.of(
          NETWORK, List.of("network", "ftp", "smb", "llmnr", "nmap"),
          WEB_APP, List.of("web"),
          EMAIL_INFILTRATION, List.of("mail", "phishing"),
          DATA_EXFILTRATION, List.of("exfiltrat"),
          URL_FILTERING, List.of("bitsadmin"),
          CLOUD, List.of("aws", "azure", "gcp"));

  public static Domain getEndpoint() {
    return new Domain(ENDPOINT);
  }

  public static Domain getNetwork() {
    return new Domain(NETWORK);
  }

  public static Domain getWebApp() {
    return new Domain(WEB_APP);
  }

  public static Domain getEmailInfiltration() {
    return new Domain(EMAIL_INFILTRATION);
  }

  public static Domain getDataExfiltration() {
    return new Domain(DATA_EXFILTRATION);
  }

  public static Domain getUrlFiltering() {
    return new Domain(URL_FILTERING);
  }

  public static Domain getCloud() {
    return new Domain(CLOUD);
  }

  public static Domain getTabletop() {
    return new Domain(TABLETOP);
  }

  public static Domain getToClassify() {
    return new Domain(TOCLASSIFY);
  }

  public static List<Domain> getDomainsForTenant(Tenant tenant) {
    List<Domain> domains = new ArrayList<>();
    List<Domain> listToInsert =
        List.of(
            ENDPOINT,
            NETWORK,
            WEB_APP,
            EMAIL_INFILTRATION,
            DATA_EXFILTRATION,
            URL_FILTERING,
            CLOUD,
            TABLETOP,
            TOCLASSIFY);
    for (Domain domain : listToInsert) {
      domains.add(
          Domain.builder().name(domain.getName()).color(domain.getColor()).tenant(tenant).build());
    }
    return domains;
  }

  public static Set<Domain> getRelevantDomainsFromKeywords(String searchValue) {
    Set<Domain> domains = new HashSet<>();
    domainKeywordsMap.forEach(
        (domain, keywords) -> {
          if (foundInKeywords(keywords, searchValue)) {
            domains.add(new Domain(domain));
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
