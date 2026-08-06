package io.openaev.ocsf.parser.schema;

import lombok.Getter;

@Getter
public class Version {
    private final OcsfSchemaVersion versionNumber;

    public Version(OcsfSchemaVersion versionNumber) {
        this.versionNumber = versionNumber;
    }
}
