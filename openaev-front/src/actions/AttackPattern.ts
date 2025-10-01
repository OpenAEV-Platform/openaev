import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../utils/Action';
import {
  type AttackPattern,
  type AttackPatternCreateInput,
  type AttackPatternUpdateInput,
  type SearchPaginationInput,
} from '../utils/api-types';
import * as schema from './Schema';

const ATTACK_PATTERN_URI = '/api/attack_patterns';

export const fetchAttackPatterns = () => (dispatch: Dispatch) => {
  return getReferential(schema.arrayOfAttackPatterns, ATTACK_PATTERN_URI)(dispatch);
};

export const searchAttackPatterns = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${ATTACK_PATTERN_URI}/search`;
  return simplePostCall(uri, data);
};

export const searchAttackPatternsWithAIWebservice = (files: File[], text: string) => {
  const formData = new FormData();
  files.forEach((file) => {
    formData.append('files', file);
  });
  formData.append('text', new Blob([JSON.stringify({ text })], { type: 'application/json' }));
  return simplePostCall(`${ATTACK_PATTERN_URI}/search-with-ai`, formData);
};

export const updateAttackPattern = (attackPatternId: AttackPattern['attack_pattern_id'], data: AttackPatternUpdateInput) => (dispatch: Dispatch) => {
  const uri = `${ATTACK_PATTERN_URI}/${attackPatternId}`;
  return putReferential(schema.attackPattern, uri, data)(dispatch);
};

export const addAttackPattern = (data: AttackPatternCreateInput) => (dispatch: Dispatch) => {
  return postReferential(schema.attackPattern, ATTACK_PATTERN_URI, data)(dispatch);
};

export const deleteAttackPattern = (attackPatternId: AttackPattern['attack_pattern_id']) => (dispatch: Dispatch) => {
  const uri = `${ATTACK_PATTERN_URI}/${attackPatternId}`;
  return delReferential(uri, 'attackpatterns', attackPatternId)(dispatch);
};

// -- OPTION --

export const searchAttackPatternsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${ATTACK_PATTERN_URI}/options`, { params });
};

export const searchAttackPatternsByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${ATTACK_PATTERN_URI}/options`, ids);
};
