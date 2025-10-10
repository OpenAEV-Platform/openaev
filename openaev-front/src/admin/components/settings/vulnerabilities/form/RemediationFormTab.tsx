import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';

const RemediationFormTab = () => {
  const { t } = useFormatter();

  return (
    <>
      <TextFieldController variant="standard" name="cve_remediation" label={t('Vulnerability Remediation')} multiline />
    </>
  );
};
export default RemediationFormTab;
