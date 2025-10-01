import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type SearchPaginationInput, type TagRuleInput, type TagRuleOutput } from '../../utils/api-types';

const TAG_RULES_URI = '/api/tag-rules';

export const addTagRule = (data: TagRuleInput) => {
  return simplePostCall(`${TAG_RULES_URI}`, data);
};

export const updateTagRule = (
  tagRuleId: TagRuleOutput['tag_rule_id'],
  data: TagRuleInput,
) => {
  const uri = `${TAG_RULES_URI}/${tagRuleId}`;
  return simplePutCall(uri, data);
};

export const deleteTagRule = (tagRuleId: TagRuleOutput['tag_rule_id']) => {
  const uri = `${TAG_RULES_URI}/${tagRuleId}`;
  return simpleDelCall(uri);
};

export const fetchTagRules = () => {
  return simpleCall(TAG_RULES_URI);
};

export const searchTagRules = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${TAG_RULES_URI}/search`;
  return simplePostCall(uri, data);
};
