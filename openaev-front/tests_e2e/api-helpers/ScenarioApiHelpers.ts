import { type APIRequestContext } from '@playwright/test';

class ScenarioApiHelpers {
  readonly scenarioUri = '/api/scenarios';
  constructor(private request: APIRequestContext) {}

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

  async deleteScenario(id: string) {
    await this.request.delete(`${this.scenarioUri}/${id}`);
  }
}

export default ScenarioApiHelpers;
