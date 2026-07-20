import {
  BugReportOutlined,
  EmojiEventsOutlined,
  GppGoodOutlined,
  HelpOutlineOutlined,
  NewspaperOutlined,
  SensorsOutlined,
  SupportAgentOutlined,
  TaskAltOutlined,
} from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType, type CSSProperties, type ReactElement } from 'react';

// Single source of truth for expectation-type icons (coherent Outlined set),
// each chosen to read clearly at a glance:
// - Prevention: shield with a check (the attack was blocked)
// - Detection: sensor waves (the attack was picked up / alerted on)
// - Vulnerability: a bug report
// - Human response: a support agent (a person reacting to the attack)
// - Manual: a completed task check (manually validated outcome)
// - Article / media pressure: a newspaper
// - Challenge: a trophy (capture-the-flag style)
const EXPECTATION_TYPE_ICON: Record<string, ComponentType<SvgIconProps>> = {
  PREVENTION: GppGoodOutlined,
  DETECTION: SensorsOutlined,
  VULNERABILITY: BugReportOutlined,
  HUMAN_RESPONSE: SupportAgentOutlined,
  MANUAL: TaskAltOutlined,
  ARTICLE: NewspaperOutlined,
  CHALLENGE: EmojiEventsOutlined,
};

// Case-insensitive lookup returning the icon component itself.
export const expectationTypeIcon = (expectationType: string | undefined): ComponentType<SvgIconProps> => {
  return EXPECTATION_TYPE_ICON[(expectationType ?? '').toUpperCase()] ?? HelpOutlineOutlined;
};

export default function expectationIconByType(expectationType: string | undefined, style: CSSProperties = {}): ReactElement {
  const IconComponent = expectationTypeIcon(expectationType);
  return <IconComponent fontSize="small" style={style} />;
};
