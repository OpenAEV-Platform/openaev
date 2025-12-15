package io.openaev.database.raw;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public interface RawKillChainPhase {

    @JsonProperty("phase_id")
    public String getPhase_id();

    @JsonProperty("phase_external_id")
    public String getPhase_external_id();

    @JsonProperty("phase_stix_id")
    public String getPhase_stix_id();

    @JsonProperty("phase_name")
    public String getPhase_name();

    @JsonProperty("phase_shortname")
    public String getPhase_shortname();

    @JsonProperty("phase_kill_chain_name")
    public String getPhase_kill_chain_name();

    @JsonProperty("phase_description")
    public String getPhase_description();

    @JsonProperty("phase_order")
    public long getPhase_order();

    @JsonProperty("phase_created_at")
    public Instant getPhase_created_at();

    @JsonProperty("phase_updated_at")
    public Instant getPhase_updated_at();
}
