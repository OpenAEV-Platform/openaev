import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';

const GeneralFormTab = () => {
  const { t } = useFormatter();

  return (
    <>
      <TextFieldController
        variant="standard"
        name="custom_dashboard_name"
        label={t('Name')}
        required
      />
      <TextFieldController
        variant="standard"
        name="custom_dashboard_description"
        label={t('Description')}
      />
    </>
  );
};

export default GeneralFormTab;
