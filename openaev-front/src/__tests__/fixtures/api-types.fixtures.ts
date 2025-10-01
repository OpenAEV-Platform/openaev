import { faker } from '@faker-js/faker';

import { type Exercise, type Organization, type Scenario, type Tag } from '../../utils/api-types';

export function createTagMap(numberTags: number): { [key: string]: Tag } {
  const tagMap: { [key: string]: Tag } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    tagMap[id] = {
      tag_id: faker.string.uuid(),
      tag_name: faker.lorem.sentence(),
    };
  }
  return tagMap;
}

export function createOrganisationsMap(numberTags: number): { [key: string]: Organization } {
  const orgMap: { [key: string]: Organization } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    orgMap[id] = {
      organization_created_at: faker.date.recent().toISOString(),
      organization_name: faker.hacker.noun(),
      organization_updated_at: faker.date.soon().toISOString(),
      organization_id: id,
    };
  }
  return orgMap;
}

export function createExercisesMap(numberTags: number): { [key: string]: Exercise } {
  const exerciseMap: { [key: string]: Exercise } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    exerciseMap[id] = {
      exercise_created_at: faker.date.recent().toISOString(),
      exercise_id: id,
      exercise_mail_from: faker.internet.email(),
      exercise_name: faker.hacker.phrase(),
      exercise_status: 'SCHEDULED',
      exercise_updated_at: faker.date.soon().toISOString(),
    };
  }
  return exerciseMap;
}

export function createScenarioMap(numberTags: number): { [key: string]: Scenario } {
  const scenarioMap: { [key: string]: Scenario } = {};
  for (let i = 0; i < numberTags; i++) {
    const id = faker.string.uuid();
    scenarioMap[id] = {
      scenario_created_at: faker.date.recent().toISOString(),
      scenario_id: id,
      scenario_mail_from: faker.internet.email(),
      scenario_name: faker.hacker.phrase(),
      scenario_updated_at: faker.date.soon().toISOString(),
    };
  }
  return scenarioMap;
}
