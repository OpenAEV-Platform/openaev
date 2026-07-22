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

// Single, harmonized identity color for EVERY expectation type: the brand blue.
// Expectation type is a category, not a result, so it must never borrow the
// result palette (green = success, orange = partial, red = failed). Keeping all
// expectation chips / icons / series the same blue makes the UI read
// unambiguously - color always means "result", shape/label always means "type".
const EXPECTATION_TYPE_IDENTITY_COLOR = '#0fbcff';

// Kept as a function (not a constant) so callers stay stable if per-type shades
// are ever reintroduced; today every expectation type resolves to the brand blue.
export const expectationTypeColor = (_expectationType?: string): string => {
  return EXPECTATION_TYPE_IDENTITY_COLOR;
};

export default function expectationIconByType(expectationType: string | undefined, style: CSSProperties = {}): ReactElement {
  const IconComponent = expectationTypeIcon(expectationType);
  return <IconComponent fontSize="small" style={style} />;
};
