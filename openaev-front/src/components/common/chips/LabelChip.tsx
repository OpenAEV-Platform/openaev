import { Chip, type ChipSeverity } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';

// The label colours of the theme dictionary on the library severity axis.
const LABEL_SEVERITY: Record<string, ChipSeverity> = {
  RED: 'critical',
  GREEN: 'low',
  ORANGE: 'medium',
};

interface Props {
  label: string;
  color: string;
}

const LabelChip: FunctionComponent<Props> = ({ label, color }) => {
  const { t } = useFormatter();
  return <Chip label={t(label)} severity={LABEL_SEVERITY[color] ?? 'neutral'} />;
};
export default LabelChip;
