package io.openaev.domain.enums;

import io.openaev.database.model.Domain;

public enum DefaultDomain {
    ENDPOINT(new Domain("Endpoint", "#389CFF")),
    NETWORK(new Domain("Network", "#009933")),
    WEB_APP(new Domain("Web App", "#FF9933")),
    EMAIL_INFILTRATION(new Domain("E-mail Infiltration", "#FF6666")),
    DATA_EXFILTRATION(new Domain("Data Exfiltration", "#9933CC")),
    URL_FILTERING(new Domain("Url Filtering", "#66CCFF")),
    CLOUD(new Domain("Cloud", "#9999CC")),
    TABLE_TOP(new Domain("Table Top", "#FFCC33")),
    UNCLASSIFIED(new Domain("Unclassified", "#969696"));

    private final Domain domain;

    DefaultDomain(Domain domain) {
        this.domain = domain;
    }

    public Domain getDomain() {
        return domain;
    }
}
