import { type FunctionComponent } from 'react';

import { updateTagRule } from '../../../../actions/tag_rules/tagrule-actions';
import {
  type TagRuleInput,
  type TagRuleOutput,
} from '../../../../utils/api-types';
import TagRuleForm from './TagRuleForm';

interface TagRuleUpdateComponentProps {
  tagRule: TagRuleOutput;
  onUpdate?: (result: TagRuleOutput) => void;
  handleClose: () => void;
}
const TagRuleUpdate: FunctionComponent<TagRuleUpdateComponentProps> = ({
  tagRule,
  onUpdate,
  handleClose,
}) => {
  const initialValues = {
    tag_name: tagRule.tag_name ?? '',
    tag_rule_id: tagRule.tag_rule_id ?? '',
    asset_groups: tagRule.asset_groups,
  };

  const onSubmit = (data: TagRuleInput) => {
    updateTagRule(tagRule.tag_rule_id, data).then(
      (result: { data: TagRuleOutput }) => {
        if (result) {
          if (onUpdate) {
            onUpdate(result.data);
          }
          handleClose();
        }
        return result;
      },
    );
  };

  return (
    <TagRuleForm
      initialValues={initialValues}
      editing
      onSubmit={onSubmit}
    />
  );
};

export default TagRuleUpdate;
