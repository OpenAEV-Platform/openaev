import { DevicesOtherOutlined, GroupsOutlined, PlayCircleOutlineOutlined, RouteOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { Binoculars, LockPattern, SelectGroup, Target } from 'mdi-material-ui';
import { type ComponentType } from 'react';

import { type EsBase } from '../../../../../../../../utils/api-types';

/**
 * Leading icon of a list-widget row, per ES entity type. Each icon mirrors the
 * one used by the entity's own list page (design system), so a simulation row
 * in a dashboard widget reads exactly like a row of the simulations list.
 */
const ENTITY_LEADING_ICONS: Record<string, ComponentType<SvgIconProps>> = {
  'asset': DevicesOtherOutlined,
  'vulnerable-endpoint': DevicesOtherOutlined,
  'asset-group': SelectGroup,
  'team': GroupsOutlined,
  'finding': Binoculars,
  'scenario': RouteOutlined,
  'simulation': PlayCircleOutlineOutlined,
  'inject': Target,
  'attack-pattern': LockPattern,
};

/** Returns the design-system icon for an ES element, falling back to the generic device icon. */
const getEntityLeadingIcon = (element: EsBase): ComponentType<SvgIconProps> =>
  ENTITY_LEADING_ICONS[element.base_entity ?? ''] ?? DevicesOtherOutlined;

export default getEntityLeadingIcon;
