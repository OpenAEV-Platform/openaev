import type React from 'react';

import { getSeverityAndColor } from '../utils/Colors';
import { useFormatter } from './i18n';

interface CvssBadgeProps { score: number }

const CVSSBadge: React.FC<CvssBadgeProps> = ({ score }) => {
  const { t } = useFormatter();
  const { severity, color } = getSeverityAndColor(score);

  return (
    <span
      style={{ color: color }}
      title={t(`CVSS Score: ${score}`)}
    >
      {`${score.toFixed(1)} ${severity}`}
    </span>
  );
};

export default CVSSBadge;
