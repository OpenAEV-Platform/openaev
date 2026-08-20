# Parameters

Parameters control the appearance, behavior, and preferences of the OpenAEV platform at runtime. Use them to apply your organization's branding, set default Dashboards, and review the health of connected services. Changes take effect immediately without restarting the application.

Navigate to **Settings > Parameters** to view and modify these settings. You need the `Manage platform settings` capability.

!!! note

    Parameters are runtime settings managed through the UI. For deployment-level configuration (environment variables, properties files), see [Configuration](../deployment/configuration.md).

## Configuration

The Configuration panel contains the core platform preferences.

| Setting | Description | Default |
|---|---|---|
| Platform name | Display name shown in the browser title and navigation bar | OpenAEV - Open Adversarial Exposure Validation Platform |
| Default theme | Theme applied to new users and the login page (`dark`, `light`, or `auto`) | dark |
| Default language | Language applied to new users (`auto` uses the browser locale) | auto |
| Home dashboard | Custom Dashboard displayed on the home page | None |
| Default scenario dashboard | Custom Dashboard used for Scenario overview pages | None |
| Default simulation dashboard | Custom Dashboard used for Simulation overview pages | None |

### How to change a setting

1. Open **Settings > Parameters**.
2. Locate the setting in the Configuration panel.
3. Update the value (select from a dropdown or type a new value).
4. Click **Save**. The change applies immediately for all users.

## Theme customization

OpenAEV supports independent customization of the **dark** and **light** themes. Each theme has its own set of colors and logos, allowing full control over the platform's visual identity.

### Colors

| Setting | Description |
|---|---|
| Background color | Main background of the application |
| Paper color | Background of cards, dialogs, and elevated surfaces |
| Navigation color | Background of the left sidebar and navigation elements |
| Primary color | Primary action color (buttons, links, active states) |
| Secondary color | Secondary action color |
| Accent color | Highlight color for emphasis and notifications |

### Logos and branding

| Setting | Description |
|---|---|
| Logo URL | Main logo displayed in the expanded sidebar |
| Logo URL (collapsed) | Compact logo displayed when the sidebar is collapsed |
| Login page logo URL | Logo displayed on the login page |

### Login page

The login page background can also be customized per theme. These settings are part of the theme customization panels and control the visual appearance of the authentication screen.

### How to customize a theme

1. Open **Settings > Parameters**.
2. Scroll to the **Dark theme** or **Light theme** panel.
3. Update color values using the color pickers or paste hex codes.
4. Paste logo URLs for the sidebar, collapsed sidebar, and login page.
5. Click **Save**. The updated theme is applied immediately.

## Platform information

The Parameters page includes a read-only panel showing technical information about the running instance. Use this panel to verify the platform version, edition, and AI configuration.

| Field | Description |
|---|---|
| Tenant identifier | UUID of the current Tenant context |
| Platform identifier | Unique identifier of the OpenAEV instance |
| Version | Current platform version |
| Edition | Community or Enterprise Edition |
| AI Powered | Whether AI capabilities are enabled and which provider is configured |

## Tools

The Tools panel displays the versions and availability status of the backend services connected to the platform. Use this panel for diagnostics when troubleshooting connectivity or compatibility issues.

| Field | Description |
|---|---|
| JVM (Java Virtual Machine) | JVM version running the backend |
| PostgreSQL | Database server version |
| RabbitMQ | Message broker version |
| Analytics engine | Elasticsearch or OpenSearch version |
| Telemetry manager | Whether telemetry collection is enabled |
| SMTP (Simple Mail Transfer Protocol) | Whether the outgoing email service is available |
| IMAP (Internet Message Access Protocol) | Whether the incoming email service is available |

## Enterprise Edition settings

!!! tip "Enterprise Edition"

    The following settings require a valid Enterprise Edition license.

| Setting | Description |
|---|---|
| Remove Filigran logos | Enables white-labeling by hiding Filigran branding throughout the interface |
| AI chatbot terms of service | Acceptance status of the Filigran AI chatbot terms of use |

## Tenant-specific parameters

When multi-tenancy is enabled, each Tenant can override a subset of platform parameters. Tenant-level settings take precedence over platform defaults for users operating within that Tenant context.

**Overridable settings (with platform fallback):**

| Setting | Fallback behavior |
|---|---|
| Platform name | Falls back to the platform-level name if not set |
| Default theme | Falls back to the platform-level theme if not set |
| Default language | Falls back to the platform-level language if not set |

**Tenant-only settings (no platform fallback):**

| Setting | Description |
|---|---|
| Home dashboard | Custom home Dashboard for this Tenant |
| Scenario dashboard | Custom Scenario Dashboard for this Tenant |
| Simulation dashboard | Custom Simulation Dashboard for this Tenant |

Each Tenant also has its own theme customization panels (colors and logos) for both dark and light themes, independent of the platform-level themes.

### How to override a setting for a Tenant

1. Navigate to **Settings > Parameters** within the Tenant context.
2. Update the desired setting (platform name, theme, language, or Dashboards).
3. Click **Save**. The Tenant-level value takes precedence over the platform default.

To revert to the platform default, clear the Tenant-level value.

## What's next?

- [Policies](policies.md) -- Configure login messages and consent banners
- [Enterprise Edition](enterprise.md) -- Activate and manage your EE license
- [Multi-tenancy](multi-tenancy.md) -- Manage isolated workspaces and Tenant settings
- [Configuration](../deployment/configuration.md) -- Deployment-level configuration (environment variables, properties)
