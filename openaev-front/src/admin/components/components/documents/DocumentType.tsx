import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { stringToColour } from '../../../../utils/Colors';

interface Props {
  type?: string;
  variant?: string;
  disabled?: boolean;
}

const DocumentType: FunctionComponent<Props> = ({ type, disabled = false }) => {
  const { t } = useFormatter();

  if (type) {
    const color = stringToColour(type);
    return (
      <Chip label={type} color={color} />
    );
  }

  return (
    <Chip label={disabled ? t('Disabled') : t('Unknown')} />
  );
};

export default DocumentType;
