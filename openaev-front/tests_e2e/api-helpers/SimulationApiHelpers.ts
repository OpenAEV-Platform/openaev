import { type APIRequestContext } from '@playwright/test';

import {
  type CreateExerciseInput,
  type Exercise,
  type ExerciseUpdateStatusInput,
  type InjectExpectationOutput,
  type InjectInput,
  type InjectOutput,
} from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class SimulationApiHelpers {
  readonly simulationUri = tenantApiPath('/api/exercises');
  constructor(private request: APIRequestContext) {}

  async createSimulation(name?: string): Promise<Exercise> {
    const input: CreateExerciseInput = { exercise_name: name || `Simulation test e2e ${Date.now()}` };
    const response = await this.request.post(this.simulationUri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to create simulation (POST ${this.simulationUri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }

  async createInject(simulationId: string, input: InjectInput): Promise<InjectOutput> {
    const uri = `${this.simulationUri}/${simulationId}/injects`;
    const response = await this.request.post(uri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to create inject (POST ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }

  async startSimulation(id: string): Promise<Exercise> {
    const uri = `${this.simulationUri}/${id}/status`;
    const input: ExerciseUpdateStatusInput = { exercise_status: 'RUNNING' };
    const response = await this.request.put(uri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to start simulation (PUT ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }

  async getExpectations(id: string): Promise<InjectExpectationOutput[]> {
    const uri = `${this.simulationUri}/${id}/expectations`;
    const response = await this.request.get(uri);
    if (!response.ok()) {
      throw new Error(`Failed to get simulation expectations (GET ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }

  async deleteSimulation(id: string) {
    await this.request.delete(`${this.simulationUri}/${id}`);
  }
}

export default SimulationApiHelpers;
