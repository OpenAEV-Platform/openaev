import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { hexToRGB, stringToColour } from '../../../../utils/Colors';

const chipStyle: CSSProperties = {
  fontSize: 12,
  height: 25,
  marginRight: 7,
  textTransform: 'uppercase',
  borderRadius: 4,
  width: 180,
};

const chipInListStyle: CSSProperties = {
  fontSize: 12,
  height: 20,
  float: 'left',
  textTransform: 'uppercase',
  borderRadius: 4,
  width: 130,
};

interface Props {
  type?: string;
  variant?: string;
  disabled?: boolean;
}

const DocumentType: FunctionComponent<Props> = ({ type, variant, disabled = false }) => {
  const { t } = useFormatter();
  const style = variant === 'list' ? chipInListStyle : chipStyle;

  if (type) {
    const color = stringToColour(type);
    return (
      <Chip
        variant="outlined"
        label={type}
        style={{
          ...style,
          color,
          borderColor: color,
          backgroundColor: hexToRGB(color),
        }}
      />
    );
  }

  return (
    <Chip
      variant="outlined"
      style={style}
      label={disabled ? t('Disabled') : t('Unknown')}
    />
  );
};

export default DocumentType;
