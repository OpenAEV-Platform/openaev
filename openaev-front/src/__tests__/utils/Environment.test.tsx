import { faker } from '@faker-js/faker';
import { describe, expect, it } from 'vitest';

import { exportData } from '../../utils/Environment';
import {
  createExercisesMap,
  createOrganisationsMap,
  createScenarioMap,
  createTagMap,
} from '../fixtures/api-types.fixtures';

/* eslint-disable-next-line  @typescript-eslint/no-explicit-any */
type testobj = { [key: string]: any };
function createObjWithDefaultKeys(objtype: string): testobj {
  const obj: testobj = {};
  ['name', 'extra_prop_1', 'extra_prop_2'].forEach((prop) => {
    obj[`${objtype}_${prop}`] = faker.lorem.sentence();
  });
  return obj;
}

describe('exportData tests', () => {
  describe('when exporting a test object', () => {
    const objtype = 'testobj';

    describe('when only a single key from filter found in object', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );
      const line = result[0];

      it('returns line with single column', async () => {
        expect(line[`${objtype}_name`]).toBe(obj[`${objtype}_name`]);
      });

      it('returns line with no other keys than specified', () => {
        Object.keys(obj).forEach(k =>
          keys.includes(k)
          // eslint-disable-next-line vitest/no-conditional-expect
            ? expect(Object.keys(line)).toContain(k)
              // eslint-disable-next-line vitest/no-conditional-expect
            : expect(Object.keys(line)).not.toContain(k),
        );
      });
    });
    describe('when testobj_type is null', () => {
      const obj = createObjWithDefaultKeys(objtype);

      obj[`${objtype}_type`] = null;

      const keys = [
        `${objtype}_name`,
        `${objtype}_type`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );
      const line = result[0];

      it('sets testobj_type to deleted', () => {
        expect(line[`${objtype}_type`]).toBe('deleted');
      });
    });

    describe('when object does not have tags', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        createTagMap(3),
      );
      const line = result[0];
      it('does not incorporate tags in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_tags`);
      });
    });

    describe('when object has tags', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const tagMap = createTagMap(3);
      obj[`${objtype}_tags`] = Object.keys(tagMap);

      // the goal is to concatenate tag names in the export
      const expected_tag_names = Object.keys(tagMap)
        .map(k => tagMap[k].tag_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        tagMap,
      );

      const line = result[0];

      it('has key _tags in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_tags`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_tags`]).toBe(expected_tag_names);
      });
    });

    describe('when object has unknown tag', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const tagMap = createTagMap(3);
      obj[`${objtype}_tags`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_tag_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_tags`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        tagMap,
      );

      const line = result[0];

      it('has key _tags in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_tags`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_tags`]).toBe(expected_tag_names);
      });
    });

    describe('when object does not have organisation', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate orgs in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_organization`);
      });
    });

    describe('when object has organizations', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const orgMap = createOrganisationsMap(3);
      obj[`${objtype}_organization`] = Object.keys(orgMap)[1];

      // the goal is to concatenate org names in the export
      const expected_org_name = orgMap[Object.keys(orgMap)[1]].organization_name;

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        orgMap,
      );

      const line = result[0];

      it('has key _organization in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_organization`);
      });

      it('incorporates matching orgs from map into line', () => {
        expect(line[`${objtype}_organization`]).toBe(expected_org_name);
      });
    });

    describe('when object has unknown organisation', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const orgMap = createOrganisationsMap(3);
      obj[`${objtype}_organization`] = faker.string.uuid(); // not found in org map

      // the goal is to concatenate tag names in the export
      const expected_org_name = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_organization`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        orgMap,
      );

      const line = result[0];

      it('has key _organization in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_organization`);
      });

      it('incorporates matching org from map into line', () => {
        expect(line[`${objtype}_organization`]).toBe(expected_org_name);
      });
    });

    describe('when object does not have exercises', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_exercises`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate exercises in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_exercises`);
      });
    });

    describe('when object has exercises', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const exerciseMap = createExercisesMap(3);
      obj[`${objtype}_exercises`] = Object.keys(exerciseMap);

      // the goal is to concatenate tag names in the export
      const expected_exercise_names = Object.keys(exerciseMap)
        .map(k => exerciseMap[k].exercise_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_exercises`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        null, // orgMap
        exerciseMap,
      );

      const line = result[0];

      it('has key _exercises in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_exercises`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_exercises`]).toBe(expected_exercise_names);
      });
    });

    describe('when object has unknown exercise', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const exerciseMap = createExercisesMap(3);
      obj[`${objtype}_exercises`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_exercise_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_exercises`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        null, // orgMap
        exerciseMap,
      );

      const line = result[0];

      it('has key _exercises in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_exercises`);
      });

      it('incorporates matching exercises from map into line', () => {
        expect(line[`${objtype}_exercises`]).toBe(expected_exercise_names);
      });
    });

    describe('when object does not have scenarios', () => {
      const obj = createObjWithDefaultKeys(objtype);

      const keys = [
        `${objtype}_name`,
        `${objtype}_scenarios`,
      ];
      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('does not incorporate scenarios in line', () => {
        expect(Object.keys(line)).not.toContain(`${objtype}_scenarios`);
      });
    });

    describe('when object has scenarios', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const scenarioMap = createScenarioMap(3);
      obj[`${objtype}_scenarios`] = Object.keys(scenarioMap);

      // the goal is to concatenate tag names in the export
      const expected_scenario_names = Object.keys(scenarioMap)
        .map(k => scenarioMap[k].scenario_name)
        .join(',');

      const keys = [
        `${objtype}_name`,
        `${objtype}_scenarios`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        null, // orgMap
        null, // exerciseMap
        scenarioMap,
      );

      const line = result[0];

      it('has key _scenarios in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_scenarios`);
      });

      it('incorporates matching tags from map into line', () => {
        expect(line[`${objtype}_scenarios`]).toBe(expected_scenario_names);
      });
    });

    describe('when object has unknown scenario', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const scenarioMap = createScenarioMap(3);
      obj[`${objtype}_scenarios`] = [faker.string.uuid(), faker.string.uuid()]; // not found in tag map

      // the goal is to concatenate tag names in the export
      const expected_scenario_names = '';

      const keys = [
        `${objtype}_name`,
        `${objtype}_scenarios`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
        null, // tagMap
        null, // orgMap
        null, // exerciseMap
        scenarioMap,
      );

      const line = result[0];

      it('has key _scenarios in line', () => {
        expect(Object.keys(line)).toContain(`${objtype}_scenarios`);
      });

      it('incorporates matching scenarios from map into line', () => {
        expect(line[`${objtype}_scenarios`]).toBe(expected_scenario_names);
      });
    });
  });

  describe('when exporting an object of type inject', () => {
    const objtype = 'inject';

    describe('when inject has an object content', () => {
      const obj = createObjWithDefaultKeys(objtype);
      const object_content = {
        key1: 'content1',
        key2: 'content2',
      };
      obj[`${objtype}_content`] = object_content;
      // mirror what's being done in the tested method
      const expected_string_content = JSON.stringify(object_content).toString().replaceAll('"', '""');

      const keys = [
        `${objtype}_name`,
        `${objtype}_content`,
      ];

      const result = exportData(
        objtype,
        keys,
        [obj],
      );

      const line = result[0];

      it('transforms content into escaped string', async () => {
        expect(line[`${objtype}_content`]).toBe(expected_string_content);
      });
    });
  });
});
