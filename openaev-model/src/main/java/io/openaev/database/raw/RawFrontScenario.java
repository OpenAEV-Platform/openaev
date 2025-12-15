package io.openaev.database.raw;

import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.ScenarioTeamUser;

import java.time.Instant;
import java.util.Set;

public interface RawFrontScenario {

    public boolean getListened();

    public String getScenario_id();

    public String getScenario_name();

    public String getScenario_category();

    public Instant getScenario_created_at();

    public Instant getScenario_updated_at();

    public String getScenario_custom_dashboard();

    public String getScenario_description();

    public String getScenario_external_url();

    public boolean getScenario_lessons_anonymized();

    public String getScenario_mail_from();

    public String getScenario_main_focus();

    public String getScenario_message_footer();

    public String getScenario_message_header();

    public String getScenario_recurrence();

    public String getScenario_recurrence_start();

    public String getScenario_recurrence_end();

    public String getScenario_subtitle();

    public Set<String> getScenario_dependencies();

    public String getScenario_severity();

    public Set<String> getScenario_exercises();

    // TODO Raw
    public Set<RawKillChainPhase> getScenario_kill_chain_phases();

    public Set<String> getScenario_platforms();

    public Set<String> getScenario_tags();

    // TODO Raw
    public Set<ScenarioTeamUser> getScenario_teams_users();

    public long getScenario_users_number();

    public long getScenario_all_users_number();
}

/*
export interface Scenario {
scenario_kill_chain_phases?: KillChainPhase[]; XXXXXXXXXXXXXXXXXXX
scenario_platforms?: ( XXXXXXXXXXXXXX
        | "Linux"
        | "Windows"
        | "MacOS"
        | "Container"
        | "Service"
        | "Generic"
        | "Internal"
        | "Unknown"
        )[];
scenario_tags?: string[]; XXXXXXXXXXXXXXXXXXXX
scenario_teams_users?: ScenarioTeamUser[]; XXXXXXXXXXXXXXXXXXXX
scenario_users_number?: number; XXXXXXXXXXXXXXXXXXXXX
}
 */