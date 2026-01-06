import type { DomainHelper } from '../../../../actions/helper';
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
      <TextFieldController name="payload_name" label={t('Name')} required />
      <TextFieldController name="payload_description" label={t('Description')} multiline={true} rows={3} />
      <AttackPatternFieldController name="payload_attack_patterns" label={t('Attack patterns')} />
      <TagFieldController name="payload_tags" label={t('Tags')} />
      <DomainFieldController
        name="payload_domains"
        label={t('Payload domains')}
        domains={domainOptions}
        required
      />
      <SelectFieldController
        name="payload_expectations"
        label={t('Expectations')}
        items={expectationsItems}
        required={true}
        multiple={true}
      />
    </>
  );
};

export default GeneralFormTab;
