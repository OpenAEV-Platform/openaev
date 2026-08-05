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
 * Tenant switcher. Deliberately split in two paths, because the Enterprise
 * Edition gate and the menu cannot coexist on one trigger:
 *
 * - **EE not validated** — the row is a plain `NavbarItem` carrying the EE
 *   chip, and activating it opens the upsell dialog. No menu is mounted, so
 *   the tenant list cannot be reached without passing the gate.
 * - **EE validated** — the same row triggers the library's `Menu`, the
 *   primitive that also backs `ProductSwitcher`.
 *
 * Each tenant row is a genuine `<a href>` (`MenuItem asChild`) because
 * switching tenant IS a URL navigation — that is what keeps ⌘/Ctrl-click
 * "open this tenant in a new tab" working.
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
