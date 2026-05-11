# OpenAEV · Product Designer Agent Kit

Kit d'instructions pour un agent designer dont le rôle est de produire des **prototypes HTML haute fidélité** basés sur le design system **filigran-ui**, avant que l'ingénierie ne construise la feature.

## Ce que cet agent fait

À partir d'une demande utilisateur, il :

1. **Pose les bonnes questions** — scope, ambition visuelle, persona, interactivité
2. **S'imprègne** du design system filigran-ui (`colors_and_type.css`, visual language)
3. **Propose ≥2 directions contrastées** sur un axe significatif
4. **Présente sur un canvas pan/zoom** quand il y a plusieurs options
5. **Itère** avec des Tweaks (variations toggleables)
6. **Refuse les anti-patterns** (slop AI, scores inventés, emoji, lorem ipsum)

## Structure du kit

```
.github/instructions/product-designer/
├── AGENT.md                    ← rôle + workflow 6 étapes
├── README.md                   ← ce fichier
├── skills/
│   ├── design-process.md       ← workflow détaillé
│   ├── visual-language.md      ← langage visuel filigran-ui (8 fingerprints + platform shell)
│   ├── anti-slop.md            ← lire AVANT chaque décision visuelle
│   ├── asking-questions.md     ← quand et comment poser des questions
│   ├── component-library.md    ← composants JSX prêts à copier
│   ├── design-canvas.md        ← présentation multi-directions
│   ├── tweaks.md               ← panneau de variations toggleables
│   ├── verification.md         ← checklist non-négociable
│   └── file-organization.md    ← conventions de nommage et paths
├── templates/
│   ├── prototype.html          ← scaffold React 18 + Babel
│   ├── colors_and_type.css     ← tokens filigran-ui (source of truth)
│   └── tweaks-panel.jsx        ← panneau Tweaks réutilisable
└── examples/
    └── compliance.md           ← exemple commenté du brief → output
```

## Output

Les prototypes sont générés dans :
```
docs/design/mockups/
├── colors_and_type.css           ← copie des templates
├── <Feature Name>.html           ← prototype principal
├── <feature>-components.jsx      ← composants companion (si > 300 lignes)
└── <Feature Name> v2.html        ← itérations versionnées
```

## Design system

- **Fonts :** Geologica (titres) + IBM Plex Sans (body) + IBM Plex Mono (nombres, IDs, code)
- **Tokens :** extraits de `filigran-ui/packages/filigran-ui/src/theme.css`
- **Dark-first :** thème sombre par défaut, light via `.dark` class removal
- **Severity :** critical / high / medium / low / info / none (sémantique, jamais décoratif)
- **Entités :** chaque type a une couleur fixe (threats, arsenal, events, etc.)

## Comment démarrer

L'agent est déclenché par le fichier `product-designer-definer.md` dans le pipeline SDD d'OpenAEV, avant `product-definition`. Il peut aussi être invoqué manuellement.

## Principe directeur

> Un placeholder est mieux qu'un mauvais essai du vrai.
> Une question est mieux qu'une supposition.
> Deux options à comparer valent mieux qu'une "bonne" option.
> La cohérence avec filigran-ui prime sur l'originalité gratuite.
