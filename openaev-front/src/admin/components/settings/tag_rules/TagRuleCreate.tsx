import { type FunctionComponent, useState } from 'react';

import { addTagRule } from '../../../../actions/tag_rules/tagrule-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type TagRuleInput,
  type TagRuleOutput,
} from '../../../../utils/api-types';
import TagRuleForm from './TagRuleForm';

interface TagRuleCreateComponentProps { onCreate?: (result: TagRuleOutput) => void }
const TagRuleCreate: FunctionComponent<TagRuleCreateComponentProps> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const onSubmit = (data: TagRuleInput) => {
    addTagRule(data).then(
      (result: { data: TagRuleOutput }) => {
        if (result) {
          if (onCreate) {
            onCreate(result.data);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create an asset rule')}
      >
        <TagRuleForm
          editing={false}
          onSubmit={onSubmit}
        />
      </Drawer>
    </>
  );
};

export default TagRuleCreate;
