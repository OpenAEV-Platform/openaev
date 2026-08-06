package io.openaev.ocsf.parser.schema.source.files;

import io.openaev.ocsf.parser.PluginContext;
import io.openaev.ocsf.parser.schema.Version;

import java.io.IOException;

public class DictionaryResource extends Resource{
    public DictionaryResource(Version version, PluginContext ctx) throws IOException {
        super(version, ctx);
    }

    @Override
    protected String getResourceName() {
        return "dictionary";
    }
}
