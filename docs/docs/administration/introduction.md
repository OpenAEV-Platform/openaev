# Administration

The Administration section covers everything related to configuring the OpenAEV platform, managing users and access control, and maintaining the system. Use this page as a starting point to navigate to the relevant sub-sections.

## Platform settings

Platform settings control the appearance and behavior of the OpenAEV interface, including the platform name, default theme, language, and home dashboards. Administrators can also customize branding (logos and colors) for both light and dark themes.

[Parameters](parameters.md)

## Security

OpenAEV provides a full Role-Based Access Control (RBAC) system. Manage users, groups, and roles to control who can access and modify resources. Capabilities and grants define fine-grained permissions across the platform. Login policies allow administrators to display consent messages and login banners.

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

Taxonomies provide the reference data used across Scenarios and Simulations, including tags, kill chain phases, attack patterns (MITRE ATT&CK), and CVEs (Common Vulnerabilities and Exposures). Administrators can manage and update these taxonomies from the platform settings.

[Taxonomies](taxonomies.md)

## XTM Hub

The XTM Hub is a centralized repository of pre-built Scenarios and integrations maintained by Filigran. Connect your OpenAEV instance to the hub to browse and import ready-to-use content.

[XTM Hub](hub.md)

## Debug mode

Debug mode provides diagnostic tools for troubleshooting platform issues, including SQL tracing and JFR (Java Flight Recorder) profiling. Use these tools to investigate performance problems or unexpected behavior.

[Debug mode](debug-mode.md)

## What's next?

- [Parameters](parameters.md) -- Configure platform appearance and behavior
- [Users and RBAC](users-and-rbac.md) -- Manage users, groups, roles, and permissions
- [Policies](policies.md) -- Configure login messages and consent banners
- [Multi-tenancy](multi-tenancy.md) -- Set up isolated workspaces
- [Enterprise Edition](enterprise.md) -- Activate your EE license
