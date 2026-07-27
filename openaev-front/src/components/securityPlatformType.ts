import {
  BoltOutlined,
  DevicesOtherOutlined,
  FingerprintOutlined,
  HubOutlined,
  LanOutlined,
  SecurityOutlined,
  SmartToyOutlined,
  StorageOutlined,
} from '@mui/icons-material';
import { type ComponentType } from 'react';

import { type SecurityPlatform } from '../utils/api-types';

// Sourced from the generated API types so new platform types added
// server-side surface as a compile error here instead of drifting.
export type SecurityPlatformType = SecurityPlatform['security_platform_type'];

// Single source of truth: one distinct accent color per security platform type,
// aligned with the exposure console orbit colors. Every place that renders a
// platform type (the chip, the atomic-testing result list, the exposure console)
// must consume this map so a given type always reads with the same color.
export const SECURITY_PLATFORM_TYPE_COLORS: Record<SecurityPlatformType, string> = {
  EDR: '#0fbcff',
  XDR: '#00bcd4',
  SIEM: '#ffb300',
  SOAR: '#9575cd',
  NDR: '#26a96c',
  ISPM: '#ff7043',
  LLM_FIREWALL: '#00f1bd',
  AI_GATEWAY: '#7e57c2',
};

// One icon per security platform type.
export const SECURITY_PLATFORM_TYPE_ICONS: Record<SecurityPlatformType, ComponentType<{ sx?: object }>> = {
  EDR: DevicesOtherOutlined,
  XDR: HubOutlined,
  SIEM: StorageOutlined,
  SOAR: BoltOutlined,
  NDR: LanOutlined,
  ISPM: FingerprintOutlined,
  LLM_FIREWALL: SecurityOutlined,
  AI_GATEWAY: SmartToyOutlined,
};

// Canonical label per security platform type. Labels keep their original (upper)
// form on purpose - the raw acronym IS the label, so "SIEM" never gets re-cased
// to "Siem" and "LLM_FIREWALL" reads cleanly. Used verbatim (no CSS transform).
export const SECURITY_PLATFORM_TYPE_LABELS: Record<SecurityPlatformType, string> = {
  EDR: 'EDR',
  XDR: 'XDR',
  SIEM: 'SIEM',
  SOAR: 'SOAR',
  NDR: 'NDR',
  ISPM: 'ISPM',
  LLM_FIREWALL: 'LLM FIREWALL',
  AI_GATEWAY: 'AI GATEWAY',
};

// Case-insensitive resolution so a stored "Siem" still resolves to "SIEM".
const normalizeType = (type?: string): SecurityPlatformType | undefined => {
  const key = type?.toUpperCase() as SecurityPlatformType | undefined;
  return key && key in SECURITY_PLATFORM_TYPE_COLORS ? key : undefined;
};

// Resolve a (possibly mixed-case or unknown) raw type to its canonical color.
// Falls back to undefined so callers can pick their own neutral color.
export const securityPlatformTypeColor = (type?: string): string | undefined => {
  const key = normalizeType(type);
  return key ? SECURITY_PLATFORM_TYPE_COLORS[key] : undefined;
};

// Resolve a raw type to its canonical, original-form label (no re-casing).
export const securityPlatformTypeLabel = (type?: string): string => {
  const key = normalizeType(type);
  return key ? SECURITY_PLATFORM_TYPE_LABELS[key] : (type ?? '-');
};

// Resolve a raw type to its icon, or undefined for unknown types.
export const securityPlatformTypeIcon = (type?: string): ComponentType<{ sx?: object }> | undefined => {
  const key = normalizeType(type);
  return key ? SECURITY_PLATFORM_TYPE_ICONS[key] : undefined;
};
