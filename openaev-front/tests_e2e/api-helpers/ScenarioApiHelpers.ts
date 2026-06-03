import { type APIRequestContext } from '@playwright/test';

class ScenarioApiHelpers {
  readonly scenarioUri = '/api/scenarios';

  constructor(private readonly request: APIRequestContext) {}

  async createScenario(name?: string) {
    const response = await this.request.post(this.scenarioUri, {
      data: {
        scenario_name: name || `Scenario test e2e ${Date.now()}`,
        scenario_category: 'attack-scenario',
        scenario_main_focus: 'incident-response',
        scenario_severity: 'high',
        scenario_subtitle: '',
        scenario_description: '',
        scenario_tags: [],
        scenario_external_reference: '',
        scenario_external_url: '',
        scenario_mail_from: 'openaev-dev@test.io',
        scenario_mails_reply_to: ['openaev-dev@test.io'],
        scenario_message_header: 'SIMULATION HEADER',
        scenario_message_footer: 'SIMULATION FOOTER',
      },
    });
    return response.json();
  }

  /**
   * Creates an email inject inside a scenario.
   * Mirrors the inject creation step in ScenarioLifecycleAuditLogAspectTest.java.
   */
  async createInjectInScenario(scenarioId: string, injectTitle: string) {
    return this.request.post(`${this.scenarioUri}/${scenarioId}/injects`, {
      data: {
        inject_title: injectTitle,
        inject_injector_contract: '138ad8f8-32f8-4a22-8114-aaa12322bd09', // EmailContract.EMAIL_DEFAULT
        inject_depends_duration: 0,
      },
    });
  }

  /**
   * Replaces all teams associated to a scenario.
   * Corresponds to {@code PUT /api/scenarios/{id}/teams/replace}.
   */
  async replaceTeams(scenarioId: string, teamIds: string[]) {
    return this.request.put(
      `${this.scenarioUri}/${scenarioId}/teams/replace`,
      { data: { scenario_teams: teamIds } },
    );
  }

  /**
   * Launches a scenario by transitioning it to the running exercise state.
   * Corresponds to {@code POST /api/scenarios/{id}/exercise/running}.
   */
  async launchScenario(scenarioId: string) {
    return this.request.post(`${this.scenarioUri}/${scenarioId}/exercise/running`);
  }

  async deleteScenario(id: string) {
    await this.request.delete(`${this.scenarioUri}/${id}`);
  }
}

export default ScenarioApiHelpers;
