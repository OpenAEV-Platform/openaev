import { BugReport, HelpOutlined, Newspaper, Person, RowingOutlined, ShieldOutlined, TrackChanges } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType, type CSSProperties, type ReactElement } from 'react';

const EXPECTATION_TYPE_ICON: Record<string, ComponentType<SvgIconProps>> = {
  prevention: ShieldOutlined,
  detection: TrackChanges,
  vulnerability: BugReport,
  manual: Person,
  article: Newspaper,
  challenge: RowingOutlined,
};

export default function expectationIconByType(expectationType: string | undefined, style: CSSProperties = {}): ReactElement {
  const IconComponent = EXPECTATION_TYPE_ICON[expectationType ?? ''] ?? HelpOutlined;
  return <IconComponent fontSize="small" style={style} />;
};
