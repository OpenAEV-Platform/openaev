package io.openaev.rest.domain.enums;

import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import java.util.*;

public class PresetDomain {
  private static final Domain ENDPOINT = Domain.createStatic("Endpoint", "#389CFF");
  private static final Domain NETWORK = Domain.createStatic("Network", "#009933");
  private static final Domain WEB_APP = Domain.createStatic("Web App", "#FF9933");
  private static final Domain EMAIL_INFILTRATION =
      Domain.createStatic("E-mail Infiltration", "#FF6666");
  private static final Domain DATA_EXFILTRATION =
      Domain.createStatic("Data Exfiltration", "#9933CC");
  private static final Domain URL_FILTERING = Domain.createStatic("URL Filtering", "#66CCFF");
  private static final Domain CLOUD = Domain.createStatic("Cloud", "#9999CC");
  private static final Domain TABLETOP = Domain.createStatic("Tabletop", "#FFCC33");
  private static final Domain TOCLASSIFY = Domain.createStatic("To classify", "#FFFFFF");

  private static final Map<Domain, List<String>> domainKeywordsMap =
      Map.of(
          NETWORK, List.of("network", "ftp", "smb", "llmnr", "nmap"),
          WEB_APP, List.of("web"),
          EMAIL_INFILTRATION, List.of("mail", "phishing"),
          DATA_EXFILTRATION, List.of("exfiltrat"),
          URL_FILTERING, List.of("bitsadmin"),
          CLOUD, List.of("aws", "azure", "gcp"));

  public static Domain getEndpoint() {
    return ENDPOINT.copy();
  }

  public static Domain getNetwork() {
    return NETWORK.copy();
  }

  public static Domain getWebApp() {
    return WEB_APP.copy();
  }

  public static Domain getEmailInfiltration() {
    return EMAIL_INFILTRATION.copy();
  }

  public static Domain getDataExfiltration() {
    return DATA_EXFILTRATION.copy();
  }

  public static Domain getUrlFiltering() {
    return URL_FILTERING.copy();
  }

  public static Domain getCloud() {
    return CLOUD.copy();
  }

  public static Domain getTabletop() {
    return TABLETOP.copy();
  }

  public static Domain getToClassify() {
    return TOCLASSIFY.copy();
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
      Domain newDomain = Domain.fromTenant(tenant.getId());
      newDomain.setName(domain.getName());
      newDomain.setColor(domain.getColor());
      domains.add(newDomain);
    }
    return domains;
  }

  public static Set<Domain> getRelevantDomainsFromKeywords(String searchValue) {
    Set<Domain> domains = new HashSet<>();
    domainKeywordsMap.forEach(
        (domain, keywords) -> {
          if (foundInKeywords(keywords, searchValue)) {
            domains.add(domain.copy());
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
