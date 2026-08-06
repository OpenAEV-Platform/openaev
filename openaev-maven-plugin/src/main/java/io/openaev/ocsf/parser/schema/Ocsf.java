package io.openaev.ocsf.parser.schema;

import io.openaev.ocsf.parser.PluginContext;

import java.io.IOException;

public class Ocsf {
    private Ocsf() {}

    public static SchemaSource instance(OcsfSchemaVersion version, PluginContext ctx) throws IOException {
        return new SchemaSource(new Version(version), ctx);
    }
}
