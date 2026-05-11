# File Organization

## Project layout (in OpenAEV)

```
.github/instructions/product-designer/     ← The agent kit (this directory)
├── AGENT.md
├── README.md
├── skills/
├── templates/
│   ├── prototype.html
│   ├── colors_and_type.css
│   └── tweaks-panel.jsx
└── examples/

docs/design/mockups/                        ← Output directory for all prototypes
├── colors_and_type.css                     ← Copy of templates/colors_and_type.css
├── <Feature Name>.html                     ← Entry point (title-cased with spaces)
├── <feature>-components.jsx                ← Companion components (optional)
└── <Feature Name> v2.html                  ← Versioned iteration
```

## Naming conventions

| Type | Convention | Example |
|------|-----------|---------|
| HTML entry points | Title Case with spaces | `Compliance Overview.html` |
| JSX companion files | kebab-case | `compliance-overview-components.jsx` |
| CSS | Always `colors_and_type.css` | Never renamed or modified |
| Versioned files | Append ` v2`, ` v3` | `Compliance Overview v2.html` |

## When to split files

| JSX lines | Action |
|-----------|--------|
| < 300 | Keep everything in the HTML `<script type="text/babel">` |
| 300-600 | Split components into a `-components.jsx` file |
| > 600 | Split by feature area, not by component type |

### How to reference companion files

```html
<!-- In your HTML file -->
<script type="text/babel" src="./compliance-components.jsx"></script>
<script type="text/babel">
  // Main app uses components defined in the companion file
  const { MetricGrid, ControlTable, EvidenceDrawer } = window;
  // ...
</script>
```

### How to export from companion JSX

```jsx
// In compliance-components.jsx
const MetricGrid = ({ metrics }) => ( /* ... */ );
const ControlTable = ({ controls }) => ( /* ... */ );

// Expose to global scope (Babel scripts share window)
Object.assign(window, { MetricGrid, ControlTable });
```

## Style object naming

When defining style objects, make names **unique per file** to avoid collisions:

```jsx
// GOOD — namespaced
const complianceStyles = { container: { ... }, header: { ... } };
const evidenceStyles = { container: { ... }, row: { ... } };

// BAD — will collide
const styles = { container: { ... } };
```

## Versioning rules

- **Never overwrite** a previous version
- Copy the file: `Compliance Overview.html` → `Compliance Overview v2.html`
- Keep all versions side by side
- In the hand-off, reference which version is current

## The `colors_and_type.css` file

- Lives in `templates/` as the source of truth
- Must be **copied** to `docs/design/mockups/` when creating a first prototype
- **Never modify it** for a single prototype — it represents the shared design system
- If you think a token is missing, flag it for the design system, don't patch it locally

## Starting a new prototype

```bash
# 1. Ensure CSS is in the output directory
cp .github/instructions/product-designer/templates/colors_and_type.css docs/design/mockups/

# 2. Copy the template
cp .github/instructions/product-designer/templates/prototype.html docs/design/mockups/"Feature Name.html"

# 3. Start building in the HTML file
```
