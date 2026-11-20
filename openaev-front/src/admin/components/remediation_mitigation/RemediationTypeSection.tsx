import { Text } from '@filigran/design-system';
import { type ReactNode } from 'react';

interface Props {
  // "Hardening remediation", "Patch remediation"...
  title: string;
  // The cards of this control type. When there are none, `emptyText` explains why.
  children?: ReactNode;
  hasItems: boolean;
  emptyText: string;
}

// One control type of the Remediation area: its title, then its remediations, or why there is none.
const RemediationTypeSection = ({ title, children, hasItems, emptyText }: Props) => (
  <section style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }}
  >
    <Text variant="content-base-bold" style={{ fontSize: 15 }}>{title}</Text>
    {hasItems
      ? children
      : <Text variant="content-compact" className="text-default-secondary">{emptyText}</Text>}
  </section>
);

export default RemediationTypeSection;
