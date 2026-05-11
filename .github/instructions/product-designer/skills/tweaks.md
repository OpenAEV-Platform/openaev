# Tweaks Panel — Toggleable Variations

## When to use Tweaks vs. Canvas

| Situation | Use |
|-----------|-----|
| Structurally different designs (different layout, metaphor, hierarchy) | Design Canvas |
| Same design with parameter variations (theme, density, persona, state) | Tweaks Panel |

## Good tweak candidates

| Tweak | Values | What it changes |
|-------|--------|-----------------|
| Theme | Light / Dark | Swaps `.dark` class on root |
| Density | Compact / Default / Spacious | Padding, font sizes, row heights |
| Persona | Analyst / Executive / Auditor | Which data is emphasized, level of detail |
| Data state | Day zero / Normal / Overwhelmed / Stale | Mock data volume and status distribution |
| Severity filter | All / Critical+High / Critical only | Which items are visible |
| Motion | On / Reduced | Animations and transitions |

## Minimum required tweaks

Every prototype should include at minimum:
- **Theme** (light/dark toggle)
- **Density** (compact/default/spacious)

## Usage

```jsx
// In your prototype, import the tweaks panel components:
// (defined in templates/tweaks-panel.jsx or inline)

function App() {
  const [tweaks, setTweaks] = useTweaks({
    theme: 'dark',
    density: 'default',
    persona: 'analyst',
  });

  // Apply theme
  React.useEffect(() => {
    document.documentElement.classList.toggle('dark', tweaks.theme === 'dark');
  }, [tweaks.theme]);

  // Derive density values
  const density = {
    compact: { padding: 'var(--spacing-xs)', rowHeight: 32, fontSize: '0.75rem' },
    default: { padding: 'var(--spacing-s)', rowHeight: 40, fontSize: '0.8125rem' },
    spacious: { padding: 'var(--spacing-m)', rowHeight: 48, fontSize: '0.875rem' },
  }[tweaks.density];

  return (
    <>
      {/* Your prototype content using density values */}
      <TweaksPanel tweaks={tweaks} onChange={setTweaks}>
        <TweakRadio name="theme" options={['light', 'dark']} />
        <TweakRadio name="density" options={['compact', 'default', 'spacious']} />
        <TweakRadio name="persona" options={['analyst', 'executive', 'auditor']} />
      </TweaksPanel>
    </>
  );
}
```

## Anti-patterns

- ❌ More than 8 tweak controls (too many = decision paralysis)
- ❌ Tweaks that don't visibly change anything
- ❌ Using tweaks for structurally different layouts (use canvas instead)
- ❌ CSS-level values as tweaks (--spacing-s vs --spacing-m — too granular)
- ❌ A tweak labeled "Style" with options "Modern" / "Classic" — too vague
