# Parameters

Parameters control the appearance, behavior, and preferences of the OpenAEV platform at runtime. Use them to apply your organization's branding, set default Dashboards, and review the health of connected services. Changes take effect immediately without restarting the application.

Navigate to **Settings > Parameters**. You need `Access tenant settings` to view and `Manage tenant settings` to edit; **Remove Filigran logos** needs `Manage platform settings`.

!!! note

    Parameters are runtime settings managed through the UI. For deployment-level configuration (environment variables, properties files), see [Configuration](../reference/deployment/configuration.md).

## Configuration

The Configuration panel contains the core platform preferences.

| Setting | Description | Default |
|---|---|---|
| Platform name | Display name shown in the browser title and navigation bar | OpenAEV - Open Adversarial Exposure Validation Platform |
| Default theme | Theme applied to new users and the login page: Default, Dark or Light | Dark |
| Default language | Language applied to new users (`auto` uses the browser locale) | auto |
| Home dashboard | Custom Dashboard displayed on the home page | None |
| Default scenario dashboard | Custom Dashboard used for Scenario overview pages | None |
| Default simulation dashboard | Custom Dashboard used for Simulation overview pages | None |

### How to change a setting

1. Open **Settings > Parameters**.
2. Locate the setting in the Configuration panel.
3. Update the value (select from a dropdown or type a new value).
4. Click **Update**. The change applies immediately for all users.

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
| Text color | Main text color |

### Logos and branding

| Setting | Description |
|---|---|
| Logo URL | Main logo displayed in the expanded sidebar |
| Logo URL (collapsed) | Compact logo displayed when the sidebar is collapsed |
| Logo URL (login) | Logo displayed on the login page |

### Login page

| Setting | Description |
|---|---|
| Login aside color | Background color of the login page side panel |
| Login aside gradient start color | Start color of the side panel gradient |
| Login aside gradient end color | End color of the side panel gradient |
| Login aside image URL | Image displayed in the side panel |

### How to customize a theme

1. Open **Settings > Parameters**.
2. Scroll to the **Dark theme** or **Light theme** panel.
3. Update color values using the color pickers or paste hex codes. Colors must use the 6-digit `#RRGGBB` format (for example `#4CAF50`). Leave a color empty to use the default.
4. Paste logo URLs for the sidebar, collapsed sidebar, and login page, and set the login page side panel.
5. Click **Update**. The updated theme is applied immediately.

## OpenAEV platform

The **OpenAEV platform** panel shows technical information about the running instance. Use this panel to verify the platform version, build commit, edition, and AI configuration.

The build commit comes from the `OPENAEV_COMMIT` environment variable, set when the Docker image is built. It is not shown when the variable is empty.

| Field | Description |
|---|---|
| Tenant identifier | UUID of the current Tenant context |
| Platform identifier | Unique identifier of the OpenAEV instance |
| Version | Current platform version. When the build commit is known, hover the version to see the commit hash, and click it to copy `<version>#<commit>` |
| Edition | Community or Enterprise Edition |
| AI Powered | Whether AI capabilities are enabled and which provider is configured |
| Remove Filigran logos | Hides Filigran branding throughout the interface (Enterprise Edition) |

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

## Tenant-specific parameters

Settings apply to the current Tenant; name, theme and language fall back to the platform default when empty.

## What's next?

- [Policies](policies.md) -- Configure login messages and consent banners
- [Enterprise Edition](enterprise.md) -- Activate and manage your EE license
- [Multi-tenancy](multi-tenancy.md) -- Manage isolated workspaces and Tenant settings
- [Configuration](../reference/deployment/configuration.md) -- Deployment-level configuration (environment variables, properties)
