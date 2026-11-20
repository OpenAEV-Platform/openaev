import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationOutput } from '../../../../utils/api-types';

interface Props {
  customization: PayloadCustomizationOutput;
  onReview: () => void;
}

// Above the form of a customized copy: the Filigran version it is built from, and the newer one it can take.
const CustomizationBaseLine: FunctionComponent<Props> = ({ customization, onReview }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const update = customization.customization_update;
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1),
      marginBottom: theme.spacing(2),
    }}
    >
      <Text variant="content-base">
        {[
          t('Based on v{version}', { version: customization.customization_base_version }),
          update && t('Filigran v{version} is available', { version: update.update_version }),
        ].filter(Boolean).join(' · ')}
      </Text>
      {update && <Button size="sm" onClick={onReview}>{t('Update')}</Button>}
    </div>
  );
};

export default CustomizationBaseLine;
