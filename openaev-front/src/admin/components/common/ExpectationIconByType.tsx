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

// Single source of truth for expectation-type icons.
const EXPECTATION_TYPE_ICON: Record<string, ComponentType<SvgIconProps>> = {
  PREVENTION: GppGoodOutlined,
  DETECTION: SensorsOutlined,
  VULNERABILITY: BugReportOutlined,
  HUMAN_RESPONSE: SupportAgentOutlined,
  MANUAL: TaskAltOutlined,
  ARTICLE: NewspaperOutlined,
  CHALLENGE: EmojiEventsOutlined,
};

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

// Result colours for an expectation, taken from the library feedback palette.
// PENDING (still running) stays brighter than UNKNOWN (never measured), so the
// two neutral states keep reading apart. An unrecognised result falls back to
// UNKNOWN rather than to the partial orange, which would claim a measurement
// that was never made.
const EXPECTATION_RESULT_COLOR: Record<string, string> = {
  SUCCESS: 'var(--color-feedback-success-primary)',
  PARTIAL: 'var(--color-feedback-warning-primary)',
  PENDING: 'var(--color-feedback-neutral-primary)',
  FAILED: 'var(--color-feedback-error-primary)',
  UNKNOWN: 'var(--color-feedback-neutral-secondary)',
};

export const expectationResultColor = (result: string | undefined): string => {
  return EXPECTATION_RESULT_COLOR[result ?? ''] ?? EXPECTATION_RESULT_COLOR.UNKNOWN;
};

export default function expectationIconByType(expectationType: string | undefined, style: CSSProperties = {}): ReactElement {
  const IconComponent = expectationTypeIcon(expectationType);
  return <IconComponent fontSize="small" style={style} />;
};
