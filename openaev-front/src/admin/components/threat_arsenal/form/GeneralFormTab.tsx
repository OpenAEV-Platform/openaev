import { type DomainHelper } from '../../../../actions/domains/domain-helper';
import AttackPatternFieldController from '../../../../components/fields/AttackPatternFieldController';
import DomainFieldController from '../../../../components/fields/DomainFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import type { Domain } from '../../../../utils/api-types';

const GeneralFormTab = () => {
  const { t } = useFormatter();

  const expectationsItems = [
    {
      value: 'PREVENTION',
      label: t('Prevention'),
    },
    {
      value: 'DETECTION',
      label: t('Detection'),
    }, {
      value: 'VULNERABILITY',
      label: t('Vulnerability'),
    },
  ];

  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => {
    return helper.getDomains();
  });

  return (
    <>
      <TextFieldController name="action_name" label={t('Name')} required />
      <TextFieldController name="action_description" label={t('Description')} multiline={true} rows={3} />
      <AttackPatternFieldController name="action_attack_patterns" label={t('Attack patterns')} />
      <TagFieldController name="action_tags" label={t('Tags')} />
      <DomainFieldController
        name="action_domains"
        label={t('Domains')}
        domains={domainOptions}
        required
      />
      <SelectFieldController
        name="action_expectations"
        label={t('Expectations')}
        items={expectationsItems}
        required={true}
        multiple={true}
      />
    </>
  );
};

export default GeneralFormTab;
