# Administration

The Administration section covers the configuration of the OpenAEV platform, users and access control. Most of these settings are in the **Settings** menu:

- **Parameters**: platform name, theme, language, branding and home dashboards;
- **Security**: users, groups, roles, organizations, sessions, policies and Tenants;
- **Customization**: default Asset rules, custom domains, notifiers, lessons learned and autonomous attack;
- **Taxonomies**: tags, attack patterns, kill chain phases and vulnerabilities;
- **Data ingestion**: XLS mappers to import Scenarios;
- **Filigran Experience**: Enterprise Edition and XTM Hub.

## User profile

Each user manages their password, email address and notifications from their profile.

[User profile](profile.md)

## Platform settings

Platform settings control the appearance and behavior of the OpenAEV interface, including the platform name, default theme, language, and home dashboards. Administrators can also customize branding (logos and colors) for both light and dark themes.

[Parameters](parameters.md)

## Security

OpenAEV provides a full Role-Based Access Control (RBAC) system. Manage users, groups, and roles to control who can access and modify resources. Login policies display consent messages and login banners.

[Users and RBAC](users-and-rbac.md) | [Policies](policies.md)

## Multi-tenancy

Multi-tenancy enables a single OpenAEV instance to host multiple isolated workspaces, each with its own users, data, and integrations. This is the recommended deployment model for MSSPs (Managed Security Service Providers) and large organizations managing multiple business units.

!!! tip "Enterprise Edition"

    Multi-tenancy requires a valid Enterprise Edition license.

[Multi-tenancy](multi-tenancy.md)

## Enterprise Edition

The Enterprise Edition unlocks advanced features such as multi-tenancy, white-labeling, and AI-powered capabilities. Activation requires a license certificate provided by Filigran.

[Enterprise Edition](enterprise.md)

## Taxonomies

Taxonomies provide the reference data used across Scenarios and Simulations, including tags, kill chain phases, attack patterns (MITRE ATT&CK), and CVEs (Common Vulnerabilities and Exposures).

[Taxonomies](taxonomies.md) | [Default Asset rules](default-asset-rules.md)

## XTM Hub

The XTM Hub provides pre-built Threat Arsenal Actions and Scenarios maintained by Filigran. Connect your OpenAEV instance to the hub to deploy them in one click.

[XTM Hub](hub.md)

## Debug mode

Debug mode provides diagnostic tools for troubleshooting platform issues, including SQL tracing and JFR (Java Flight Recorder) profiling. Use these tools to investigate performance problems or unexpected behavior.

[Debug mode](debug-mode.md)

## What's next?

- [User profile](profile.md) -- Manage your password and email address
- [Parameters](parameters.md) -- Configure platform appearance and behavior
- [Users and RBAC](users-and-rbac.md) -- Manage users, groups, roles, and permissions
- [Policies](policies.md) -- Configure login messages and consent banners
- [Multi-tenancy](multi-tenancy.md) -- Set up isolated workspaces
- [Enterprise Edition](enterprise.md) -- Activate your EE license
- [Taxonomies](taxonomies.md) -- Manage tags, attack patterns, kill chain phases and vulnerabilities
- [Default Asset rules](default-asset-rules.md) -- Apply Asset groups to Injects from Scenario tags
- [XTM Hub](hub.md) -- Connect to the XTM Hub
- [Debug mode](debug-mode.md) -- Diagnose platform issues
