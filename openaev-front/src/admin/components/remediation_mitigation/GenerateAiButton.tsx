import { Button, type ButtonProps } from '@filigran/design-system';
import { SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent } from 'react';

// Same look as the "Generate with AI" button of the Remediation tab (DetectionRemediationUseAriane):
// AI variant, secondary, default size, XTM One icon, label on one line.
const GenerateAiButton: FunctionComponent<Omit<ButtonProps, 'variant' | 'priority' | 'startIcon' | 'size'>> = ({ style, ...props }) => (
  <Button
    variant="ia"
    priority="secondary"
    type="button"
    startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
    style={{
      whiteSpace: 'nowrap',
      ...style,
    }}
    {...props}
  />
);

export default GenerateAiButton;
