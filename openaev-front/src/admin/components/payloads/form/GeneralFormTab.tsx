import AttackPatternFieldController from '../../../../components/fields/AttackPatternFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';

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
    }, {
      value: 'MANUAL',
      label: t('Manual'),
    },
  ];

  return (
    <>
      <TextFieldController name="payload_name" label={t('Name')} required />
      <TextFieldController name="payload_description" label={t('Description')} multiline={true} rows={3} />
      <AttackPatternFieldController name="payload_attack_patterns" label={t('Attack patterns')} />
      <TagFieldController name="payload_tags" label={t('Tags')} />
      <SelectFieldController name="payload_expectations" label={t('Expectations')} items={expectationsItems} required={true} multiple={true} />
    </>
  );
};

export default GeneralFormTab;
