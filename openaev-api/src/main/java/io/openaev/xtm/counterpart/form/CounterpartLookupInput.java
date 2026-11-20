package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The identifiers to look up: hostnames or IPs, MITRE ids, CVE ids or STIX ids. */
public record CounterpartLookupInput(
    @JsonProperty("counterpart_keys") @NotNull @Size(max = 200) List<String> keys) {}
