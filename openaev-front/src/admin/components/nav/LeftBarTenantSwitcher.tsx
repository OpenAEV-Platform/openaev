import { Chip, Icon, Menu, MenuContent, MenuItem, MenuTrigger, NavbarItem } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { useLocation } from 'react-router';

import { NavbarItemContent } from '../../../components/common/menu/navbar/NavbarRowContent';
import { useFormatter } from '../../../components/i18n';
import type { TenantOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { buildTenantUrl, stripDetailSegments } from '../../../utils/url-helper';

interface TenantSwitcherProps { navOpen: boolean }

/**
 * Tenant switcher: the row listing the tenants the user can reach.
 *
 * Fully recomposed on design system primitives, and deliberately split in two
 * paths so neither one has to fight the other:
 *
 * - **Enterprise Edition not validated** — switching is gated, so the row is a
 *   plain `NavbarItem` carrying the EE chip; activating it opens the upsell
 *   dialog. No menu is mounted at all, so there is no way to reach the tenant
 *   list without passing the gate.
 * - **Enterprise Edition validated** — the same row becomes the trigger of the
 *   library's `Menu`, the primitive that also backs `ProductSwitcher`. The
 *   panel is therefore identical to the rest of the design system by
 *   construction rather than by imitation.
 *
 * Each tenant row is a genuine `<a href>` (`MenuItem asChild`), because
 * switching tenant IS a URL navigation (`useTenant.navigateToTenant` assigns
 * `window.location.href`). That keeps ⌘/Ctrl-click "open this tenant in a new
 * tab" working, and it is why the rows are anchors and not buttons.
 *
 * The current tenant is marked with `MenuItem`'s own `selected` state (a check
 * in the trailing slot plus `aria-current`), which is scoped to the panel and
 * never leaks onto the rail row.
 */
const TenantSwitcher: FunctionComponent<TenantSwitcherProps> = ({ navOpen }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const { userTenants, currentUserTenant } = useAuth();
  const { isValidated: isValidatedEnterpriseEdition, openDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();

  const tenants = userTenants ?? [];
  const displayName = currentUserTenant?.tenant_name ?? t('No tenant');

  // The switcher (and the EE chip it hosts) only makes sense when the user
  // can actually switch, i.e. has access to more than one tenant.
  if (tenants.length <= 1) {
    return null;
  }

  // Same destination the legacy handler navigated to: the current page minus
  // its detail segments, so the user never lands on a resource that does not
  // exist in the target tenant.
  const tenantHref = (tenant: TenantOutput) => buildTenantUrl(tenant.tenant_id, stripDetailSegments(location.pathname));

  // Anatomy of the rail row itself, shared by both paths: leading icon,
  // label, an optional badge, then the double chevron announcing the menu.
  const triggerRow = (badge?: ReactNode) => (
    <>
      <NavbarItemContent
        collapsed={!navOpen}
        icon={<Icon name="building-2" size={16} />}
        label={displayName}
      />
      {navOpen && badge}
      {navOpen && (
        <span className="text-default-secondary inline-flex shrink-0" style={{ marginLeft: 2 }} aria-hidden="true">
          <Icon name="chevrons-up-down" size={16} />
        </span>
      )}
    </>
  );

  if (!isValidatedEnterpriseEdition) {
    const openUpsellDialog = () => {
      setEEFeatureDetectedInfo('Tenants');
      openDialog();
    };
    return (
      <NavbarItem asChild tooltipLabel={displayName}>
        <button type="button" onClick={openUpsellDialog} data-testid="tenant-switcher">
          {triggerRow(<Chip label={t('EE')} tone="tonic" />)}
        </button>
      </NavbarItem>
    );
  }

  return (
    <Menu>
      <MenuTrigger asChild>
        <NavbarItem asChild tooltipLabel={displayName}>
          <button type="button" data-testid="tenant-switcher">
            {triggerRow()}
          </button>
        </NavbarItem>
      </MenuTrigger>
      <MenuContent align="start" side="right">
        {tenants.map(tenant => (
          <MenuItem
            key={tenant.tenant_id}
            asChild
            selected={tenant.tenant_id === currentUserTenant?.tenant_id}
            data-testid="tenant-switcher-option"
          >
            <a href={tenantHref(tenant)}>{tenant.tenant_name}</a>
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );
};

export default TenantSwitcher;
