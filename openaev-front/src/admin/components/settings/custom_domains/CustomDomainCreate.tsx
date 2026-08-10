import { type FunctionComponent, useState } from 'react';

import { addCustomDomain } from '../../../../actions/custom_domains/customdomain-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain, type CustomDomainInput } from '../../../../utils/api-types';
import CustomDomainForm from './CustomDomainForm';
import CustomDomainInstructionsPanel from './CustomDomainInstructionsPanel';

interface Props {
  onCreate?: (result: CustomDomain) => void;
  onUpdate?: (result: CustomDomain) => void;
}

const CustomDomainCreate: FunctionComponent<Props> = ({ onCreate, onUpdate }) => {
  const { t } = useFormatter();
  const [openForm, setOpenForm] = useState(false);
  const [created, setCreated] = useState<CustomDomain | null>(null);

  const onSubmit = (data: CustomDomainInput) => {
    return addCustomDomain(data).then((result: { data: CustomDomain }) => {
      if (result?.data) {
        if (onCreate) {
          onCreate(result.data);
        }
        setOpenForm(false);
        // Immediately surface the DNS records the user must publish to finish setup.
        setCreated(result.data);
      }
      return result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpenForm(true)} label={t('Add a custom domain')} />
      <Drawer
        open={openForm}
        handleClose={() => setOpenForm(false)}
        title={t('Add a custom domain')}
      >
        <CustomDomainForm onSubmit={onSubmit} />
      </Drawer>
      <Drawer
        open={!!created}
        handleClose={() => setCreated(null)}
        title={t('Configure DNS')}
      >
        {created && (
          <CustomDomainInstructionsPanel
            customDomain={created}
            onUpdate={(result) => {
              setCreated(result);
              onUpdate?.(result);
            }}
          />
        )}
      </Drawer>
    </>
  );
};

export default CustomDomainCreate;
