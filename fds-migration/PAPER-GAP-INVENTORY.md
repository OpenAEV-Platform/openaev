# Inventaire des écarts — Paper (lib) vs surfaces conteneur OpenAEV

Rendu **avant** toute conversion, comme prérequis bloquant de la vague pilote
Paper. Rien n'a été converti : la migration ne démarre qu'après arbitrage
écart par écart.

- Produit : OpenAEV, branche `fds/paper-pilot` sur `design-system/current`
  (base `dd758996369665e479453bdf71620c19a61cab88`).
- Lib : `@filigran/design-system` **pin `35a476849ba72d48cacae2568643f0b5638bc468`**
  (celui déjà épinglé dans `openaev-front/package.json`).
- Toutes les valeurs ci-dessous sont **mesurées sur le build installé** et sur
  les composants réels du produit montés dans le vrai thème MUI
  (`ThemeDark`/`ThemeLight`, `spacing: 8`) — jamais lues dans les types, le
  changelog ou la doc.
- Périmètre mesuré : les 10 surfaces du lot `admin/components/lessons` et les
  4 balises `Paper` de `components/common/detail/EntityDetailCommon.tsx`.

---

## 0. Étape 0 — ce que le Paper de la lib sait faire aujourd'hui

Mesuré en rendant le `Paper` **du build installé** (`node_modules/@filigran/
design-system/packages/filigran-design-system/dist/index.js`) et en relevant
les styles calculés dans le navigateur.

| Capacité attendue | État | Preuve mesurée |
|---|---|---|
| prop `padding`, échelle 0 / 8 / 16 / 24 / 32 | ❌ **absente** | `<Paper padding={16}>` rend `class="… p-6 …" padding="16"` : la valeur **fuit en attribut DOM** et n'a aucun effet. Padding calculé : **24px dans tous les cas**. |
| élévations 0-3 | ✅ **présentes** | `elevation={n}` → classe `layer-n`. Fonds calculés — dark : `#070d18` / `#0d172b` / `#13213e` / `#1f3965` ; light : `#f2f2f3` / `#ffffff` / `#f4f4f6` / `#e4e5e7`. Quatre niveaux réellement distincts dans les deux thèmes. |
| prop `title` | ❌ **absente** | `<Paper title="X">` ne rend aucun en-tête : la valeur retombe sur l'**attribut HTML natif `title`**, c'est-à-dire une infobulle navigateur au survol de tout le panneau. Régression silencieuse pour qui l'utiliserait. |
| prop `action` | ❌ **absente** | `<Paper action={<button/>}>` sort `action="[object Object]"` sur un `<div>` — attribut invalide, React émet un avertissement. |

Autres constantes relevées sur le build installé, non demandées mais
structurantes pour la suite :

| Propriété | Valeur du Paper lib | Remarque |
|---|---|---|
| rayon | `4px` (`rounded-sm`) | identique au produit |
| ombre | `none` à tous les niveaux | identique au produit |
| bordure | toujours dessinée, non désactivable | voir écart **G2** |
| `box-sizing` | `border-box` | conforme |
| props publiques | `elevation`, `as`, `className`, `children` | aucune autre |

**Verdict étape 0 : GATE ROUGE.** Deux des trois capacités demandées manquent
(`padding`, `title`/`action`). Rien n'a été bricolé côté produit.

### 0 bis — l'échappatoire `className` ne referme pas le trou

`Paper.meta.ts` documente `className` comme le moyen de surcharger `p-6`
(« e.g. override the default p-6 »). Mesuré sur la feuille livrée
(`dist/index.css`), les utilitaires de padding réellement présents sont :

| classe | valeur calculée | échelle demandée |
|---|---|---|
| `p-0` | 0px | ✅ 0 |
| `p-1` | 4px | — |
| `p-2` | 8px | ✅ 8 |
| `p-3` | 12px | — |
| `p-4` | 16px | ✅ 16 |
| `p-6` | 24px | ✅ 24 (défaut) |
| **`p-8`** | **0px — la classe n'existe pas dans la feuille livrée** | ❌ 32 inexprimable |
| `p-16` | 64px | — |

Donc : 0/8/16/24 sont atteignables *par une classe en dur*, 32 ne l'est pas du
tout. Et le produit ne compile pas Tailwind (voir LIBRARY-FEEDBACK #13) : il
consomme la feuille pré-construite, une classe inventée ne résoudrait rien.
Passer par `className="p-4"` reviendrait exactement à réintroduire le padding
en dur que la garde de conformité doit faire rougir — c'est une compensation,
pas une capacité.

---

## 1. Les sites réels, relevés un par un

### Lot 1 — `admin/components/lessons` (10 surfaces)

| id | site | padding produit | rayon | bordure | fond | autres |
|---|---|---|---|---|---|---|
| L1 | `LessonsObjectives.jsx:26` | **0** | 4px | 1px | paper | `overflow:hidden`, `flex:1`, hôte d'une `List` à séparateurs |
| L2 | `simulations/CrysisIntensity.jsx:35` | **0** | 4px | 1px | paper | `overflow:hidden`, `flex:1`, hôte d'un graphe ApexCharts pleine largeur |
| L3 | `simulations/LessonsCategories.jsx:140` | **0** | 4px | 1px | paper | `overflow:hidden`, `flex:1`, `List` |
| L4 | `simulations/LessonsCategories.jsx:203` | **0** | 4px | 1px | paper | `overflow:hidden`, `flex:1`, `List` |
| L5 | `simulations/LessonsCategories.jsx:306` | **16** | 4px | 1px | paper | `display:flex`, `flexWrap`, `gap:8`, `alignContent:flex-start` |
| L6 | `scenarios/LessonsCategories.jsx:115` | **0** | 4px | 1px | paper | `overflow:hidden`, `flex:1`, `List` |
| L7 | `scenarios/LessonsCategories.jsx:189` | **16** | 4px | 1px | paper | `display:flex`, `flexWrap`, `gap:8` |
| L8 | `simulations/Lessons.tsx:152` | **16** | 4px | 1px | paper | barre de `HeroStats` |
| L9 | `simulations/Lessons.tsx:355` | **0** | 4px | 1px | paper | placeholder centré |
| L10 | `scenarios/Lessons.tsx:217` | **0** | 4px | 1px | paper | placeholder centré |

Répartition des paddings du lot : **7 sites à 0px, 3 sites à 16px, 0 site à 24px**.

Note : les `PaperProps={{ elevation: 1 }}` croisés dans ce dossier
(`CreateObjective.jsx:50`, `LessonsApplyTemplateDialog.tsx:46`,
`ObjectivePopover.jsx:104`, `Lessons.tsx:385/407/427/451/482`,
`CreateLessonsCategory.jsx:63`, `CreateLessonsQuestion.jsx:80`) portent sur le
papier interne d'un `Dialog`/`Popover` MUI, pas sur une balise `Paper` : hors
périmètre de cette vague.

### Lot 2 — `components/common/detail/EntityDetailCommon.tsx` (4 balises)

| id | balise | ligne | padding produit | rayon | particularités |
|---|---|---|---|---|---|
| E1 | `Section` | 36 | **16** | 4px | titre **hors surface** (au-dessus), `flex:1` |
| E2 | `InformationGrid` | 91 | **16** | 4px | titre + `action` hors surface ; la surface est elle-même la grille (`grid-template-columns: repeat(auto-fit, minmax(180px,1fr))`, `gap:12`, `rowGap:16`, `alignContent:start`) |
| E3 | `SectionBlock` | 188 | **16 ou 0** (`disablePadding`) | 4px | titre + `action` hors surface ; `centerContent` transforme la surface en conteneur flex centré |
| E4 | `DetailHero` | 356 | **16** | 4px | **dégradé d'accent** `linear-gradient(135deg, alpha(primary,0.08), transparent 60%)` + `background-color` annulé, `flex column`, `gap:16`, `data-testid="detail-hero"` |

Portée mesurée sur cette branche : **127 usages dans 42 fichiers**
(`Section` 24/10, `InformationGrid` 21/16, `SectionBlock` 61/20,
`DetailHero` 21/21) — un peu plus que les « 112 écrans dans 38 fichiers »
annoncés au cadrage, la branche ayant avancé depuis.

---

## 2. Les écarts, un par un

Chaque écart : le site, sa fréquence, la valeur produit, la valeur lib, et
l'effet mesuré. Les planches AVANT/APRÈS (même cadrage, dark / light / thème
client) sont jointes au rapport de session, hors de l'arbre.

---

### G1 — Padding : la lib impose 24px, aucun site du périmètre n'est à 24px

| | |
|---|---|
| **Sites** | les 14 du périmètre |
| **Fréquence** | **14/14 — 100 %** |
| **Valeur produit** | 0px (9 sites) ou 16px (5 sites) |
| **Valeur lib** | 24px, non paramétrable |
| **Effet mesuré** | +24px de padding sur les 9 sites nus, +8px sur les 5 sites à 16px |

Conséquences concrètes, visibles sur les planches :

- Les 7 surfaces `lessons` à padding 0 hébergent des `List` **à séparateurs
  pleine largeur** (ou un graphe pleine largeur, L2). Avec 24px, les
  séparateurs se détachent des bords et le graphe se retrouve encadré de
  blanc : ce n'est pas une densité différente, c'est un motif visuel
  différent.
- Sur E2 (`InformationGrid`), la surface **est** la grille : +8px de padding
  décale toute la grille et peut faire retomber une colonne (les pistes font
  `minmax(180px, 1fr)`).
- La consigne de vague est explicitement ISO (« aucun écran ne doit changer de
  densité »). Sans prop `padding`, ISO est **inatteignable** sans réintroduire
  un padding en dur — donc sans déclencher la garde qu'on doit précisément
  installer.

**Ce qu'il faudrait côté lib** : la prop `padding` de l'étape 0, échelle
0/8/16/24/32 (24 restant le défaut). L'échelle demandée couvre exactement les
valeurs mesurées ici (0 et 16) et le 32 aujourd'hui inexprimable même en dur.

---

### G2 — Bordure : OpenAEV en dessine une, mais pas la même

Vérification demandée : côté OpenCTI les panneaux rendent **sans** bordure
alors que le Paper lib en dessine une. **Côté OpenAEV, c'est différent** : les
14 sites utilisent `variant="outlined"` et rendent **avec** une bordure.
L'écart n'est donc pas la présence, c'est la **couleur** — et elle n'est pas
symétrique entre les deux thèmes.

| | produit (MUI `outlined`) | Paper lib | composite |
|---|---|---|---|
| dark | `rgba(255,255,255,0.12)` sur `#0d172b` | `rgba(43,79,141,0.1)` sur `#0d172b` | `#2a3344` → **`#101d35`** |
| light | `rgba(0,0,0,0.12)` sur `#ffffff` | `rgba(228,229,231,0.1)` sur `#ffffff` | `#e0e0e0` → **`#fcfcfd`** |

Contraste bordure/surface mesuré :

| thème | produit | Paper lib |
|---|---|---|
| dark | **1,41:1** | **1,06:1** |
| light | **1,32:1** | **1,03:1** |

- **Fréquence : 14/14 — 100 %.**
- En **light**, le résultat est une bordure **pratiquement invisible**
  (1,03:1) : sur la planche light, les panneaux APRÈS flottent en blanc sur le
  fond gris clair, sans contour. C'est l'écart le plus visible du lot après le
  thème client.
- En **dark**, la bordure passe d'un gris froid à un bleu très sombre, deux
  fois moins contrasté.
- Aucune de ces valeurs n'est un critère WCAG (surface non interactive, et la
  lib documente explicitement sa bordure comme décorative et non gatante) —
  c'est un écart **de rendu**, pas de conformité. Mais « décoratif » côté lib
  et « seul contour du panneau » côté produit ne sont pas la même fonction.

**Ce qu'il faudrait côté lib** : soit une bordure dont la couleur reste
perceptible en light, soit un moyen supporté de ne pas la dessiner (le produit
ne peut pas la neutraliser : `border-*` n'est pas dans les utilitaires
livrés — LIBRARY-FEEDBACK #13).

---

### G3 — Fond : le Paper lib **ignore le thème client**

C'est l'écart le plus lourd de l'inventaire, et il n'est visible que sur une
install à thème personnalisé.

Mesuré avec un thème client (`platform_dark_theme` : `background #2b1a3d`,
`paper #3b2450`, `primary #ff8a3d`) passé à `themeDark()` exactement comme le
fait `AppThemeProvider` :

| | fond mesuré |
|---|---|
| produit (MUI Paper) | **`rgb(59,36,80)` = `#3b2450`** — la couleur du client |
| Paper lib | **`rgb(13,23,43)` = `#0d172b`** — le défaut Filigran |

- **Fréquence : 14/14 sur toute install ayant un `paper_color` personnalisé.**
- Contraste entre les deux fonds : **1,32:1** — soit, sur la planche « thème
  client », des panneaux violets (non migrés) juxtaposés à des panneaux bleu
  Filigran (migrés). Rupture de charte immédiate, par tenant.
- Sur les thèmes par défaut, en revanche, le fond est **identique au pixel**
  (`#0d172b` en dark, `#ffffff` en light) : le pont de tokens
  (`fds-tokens.generated.ts`) a déjà aligné `background.paper` sur
  `--bg-elevation-default-layer-1`. C'est précisément ce qui rend l'écart
  invisible tant qu'on ne teste pas un thème client.
- `migration-state.json` note que dans **cet** environnement tous les champs
  `platform_*_theme` sont nuls ; ce n'est pas une garantie de déploiement, le
  réglage existe dans l'UI d'administration.

**Ce qu'il faudrait côté lib** : le hook déjà demandé en LIBRARY-FEEDBACK #17
pour `Header`, généralisé aux surfaces — une propriété personnalisée lue par
le composant (`--fds-paper-background`), ou une garantie documentée qu'un
consommateur peut redéclarer le token de fond par élément.

---

### G4 — `DetailHero` : dégradé d'accent et fond transparent, perdus

| | |
|---|---|
| **Site** | E4 `DetailHero` (`EntityDetailCommon.tsx:356`) |
| **Fréquence** | **21 écrans, 21 fichiers** — le héros de toutes les pages de détail |
| **Valeur produit** | `background: linear-gradient(135deg, alpha(primary,0.08), transparent 60%)` **et** `background-color` calculé à `rgba(0,0,0,0)` (le raccourci `background` du `sx` écrase le fond du papier) |
| **Valeur lib** | fond plat `bg-elevation-default`, aucun dégradé, aucune prise |
| **Effet mesuré** | dark : `linear-gradient(135deg, rgba(66,202,255,0.08), transparent 60%)` → `none` ; light : `rgba(0,21,168,0.08)` → `none` ; client : `rgba(255,138,61,0.08)` → `none` |

Deux pertes distinctes, pas une :

1. **Le dégradé d'accent** suit `palette.primary.main`, donc la couleur du
   client. Le Paper lib n'expose aucun moyen de le poser (et `bg-gradient-*`
   n'est pas dans les utilitaires livrés au produit).
2. **La transparence du fond** : aujourd'hui le héros laisse voir le dégradé
   de page (`MuiCssBaseline`, deux stops) ; le Paper lib pose un fond opaque.
   Même sans dégradé d'accent, le héros changerait d'aspect.

C'est le site « à traiter en dernier et à montrer » du cadrage : il est montré
ici, avant conversion, parce que l'écart est structurel et pas cosmétique.

---

### G5 — En-tête hors surface : `title`/`action` n'existent pas

| | |
|---|---|
| **Sites** | E1, E2, E3 (titre hors surface, plus `action` pour E2/E3) |
| **Fréquence** | **106 usages sur 127** (`Section` 24, `InformationGrid` 21, `SectionBlock` 61) |
| **Valeur produit** | en-tête au-dessus de la surface : `Typography` `SECTION_LABEL_SX` (Geologica 11px, 600, `letter-spacing .12em`, majuscules, `margin-bottom: 12px`), et pour `action` une rangée `min-height: 32px`, `gap: 8px`, action poussée à droite |
| **Valeur lib** | aucune prop `title`/`action` (mesuré : elles fuient en attributs DOM) |

**Ce n'est pas bloquant** : le mapping arbitré prévoit exactement ce cas —
« sinon garde l'en-tête côté produit au-dessus du Paper, à l'identique ». Les
en-têtes restent donc du code produit, inchangés, et seule la surface devient
un `Paper`. L'écart est listé pour mémoire (et parce que `title` qui devient
une infobulle navigateur est un piège à documenter), pas comme un blocage.

**Piège mesuré à signaler quand même** : un agent qui écrit
`<Paper title="Section">` en croyant utiliser une prop obtient une infobulle
sur tout le panneau, sans erreur ni au type ni au rendu.

---

### G6 — Rayon, ombre, densité, états : **aucun écart**

Vérifiés parce que demandés, et négatifs — c'est une bonne nouvelle qu'il faut
écrire :

| propriété | produit | Paper lib | écart |
|---|---|---|---|
| rayon | `4px` (`borderRadius: 1` × `shape.borderRadius: 4`) | `4px` (`rounded-sm`) | **aucun** |
| ombre | `none` (14/14 sites, `variant="outlined"`) | `none` (4 niveaux) | **aucun** |
| couleur de texte | `#f2f2f3` dark / `#18191b` light | idem | **aucun** |
| `box-sizing` | `border-box` | `border-box` | **aucun** |
| états (hover/focus/selected) | aucun sur ces 14 surfaces (aucune n'est interactive) | aucun | **aucun** |
| élévation | tous les sites sont à l'élévation « papier » par défaut | `elevation=1` par défaut, même fond | **aucun** |

Aucun des 14 sites n'est cliquable, aucun ne porte d'état, aucun n'est
semi-transparent — le périmètre choisi est bien du conteneur nu ou à
titre hors surface, pas de la carte cliquable.

---

## 3. Récapitulatif pour arbitrage

| écart | sites touchés | fréquence | bloquant pour une migration ISO ? |
|---|---|---|---|
| **G1** padding imposé à 24px | 14/14 | 100 % | **oui** |
| **G2** couleur de bordure (invisible en light) | 14/14 | 100 % | **oui** (rendu light) |
| **G3** fond insensible au thème client | 14/14 | 100 % des installs personnalisées | **oui** |
| **G4** dégradé + transparence du `DetailHero` | 1 balise → 21 écrans | 21 écrans | **oui** pour E4 |
| **G5** pas de `title`/`action` | 3 balises → 106 usages | 84 % | non (en-tête reste produit) |
| **G6** rayon / ombre / états / densité | — | — | non — aucun écart |

**Aucune conversion n'a été faite.** Les quatre écarts bloquants ci-dessus
sortent tous du même constat : le Paper de la lib est aujourd'hui une surface
**fermée** (padding fixe, bordure fixe, fond fixe), alors que les 14 sites du
périmètre sont des surfaces **paramétrées** par le produit et, pour trois
d'entre elles, par le client final.

Ce qui manque est listé ici et repris en entrées numérotées dans
`LIBRARY-FEEDBACK.md` (#26 à #30) — rien n'a été compensé côté produit.

---

## 4. Arbitrages Sandy — 2026-08-14

Rendus après lecture de l'inventaire ci-dessus. Ils **modifient le périmètre**
de la vague et ajoutent une règle de conversion.

| # | Arbitrage |
|---|---|
| **G1** padding | On **attend la prop côté lib** (PR lib en cours). Pas de vague non-ISO. **+ règle nouvelle** : quand le Paper porte le padding, le padding interne des enfants est **retiré** — pas de doublement. Traitement au cas par cas, voir §5. |
| **G2** bordure | La lib **mesure d'abord l'écart au nœud Figma**, Sandy tranche ensuite. **Rien à bricoler côté produit.** |
| **G3** fond / thème client | **Point le plus important.** Le fond du Paper doit suivre le thème hôte, **comme la Navbar et le Header**. Demandé à la lib dans la même PR. **On ne migre PAS** en acceptant la perte pour les tenants personnalisés. |
| **G4** `DetailHero` | **Hors de cette vague.** Deux pertes (dégradé d'accent + fond transparent), et le fond transparent tombe sous l'exclusion « conteneurs semi-transparents = temps 2 ». Listé, non converti. |
| **G5** gate | La modification du template `check-fds-conformity` part **côté lib** (c'est là qu'il vit), pas en script produit séparé. |
| **G6** périmètre | On part sur le **mesuré** : 127 usages / 42 fichiers pour les 4 balises. |
| **G7** cartes MUI | Aucune dans ce périmètre — la règle « Paper dans le composant parent » est **gardée pour une vague suivante**. |

### Périmètre après arbitrage

`DetailHero` sortant, la vague porte sur **13 surfaces** :

- **10** dans `admin/components/lessons` (L1 → L10) ;
- **3** balises dans `EntityDetailCommon.tsx` — `Section`, `InformationGrid`,
  `SectionBlock` — qui pilotent **106 usages dans 33 fichiers** (mesuré).

`DetailHero` (E4) reste sur MUI : 21 usages, 21 fichiers, non convertis, motif
ci-dessus.

---

## 5. Paddings enfants — les sites concernés par la règle « pas de doublement »

Relevé au DOM sur les composants réels (padding calculé des enfants directs, et
de la première ligne interne qui en porte un). Trois familles, et elles
n'appellent pas la même décision.

### 5.1 — Le padding vit dans l'enfant, le Paper est à 0 (**6 sites, à traiter**)

Si le Paper prend un padding non nul, celui de l'enfant doit partir.

| site | padding Paper | padding enfant mesuré | ce qu'il porte |
|---|---|---|---|
| **L1** `LessonsObjectives.jsx:26` | 0 | `MuiListItem` **8px 16px** | gouttières MUI des lignes |
| **L3** `simulations/LessonsCategories.jsx:140` | 0 | `MuiListItem` **8px 16px** | idem |
| **L4** `simulations/LessonsCategories.jsx:203` | 0 | `MuiListItem` **8px 16px** | idem |
| **L6** `scenarios/LessonsCategories.jsx:115` | 0 | `MuiListItem` **8px 16px** | idem |
| **L9** `simulations/Lessons.tsx:355` | 0 | `LessonsPlaceholder` **32px** (4 côtés) | marge propre du vide |
| **L10** `scenarios/Lessons.tsx:217` | 0 | `LessonsPlaceholder` **32px** (4 côtés) | idem |

Point de décision, à montrer avant de trancher :

- **L1/L3/L4/L6** — les 16px horizontaux des `ListItem` sont la marge visuelle
  du panneau, mais les lignes portent **des séparateurs pleine largeur**.
  Retirer les gouttières et donner le padding au Paper **rentre aussi les
  séparateurs** : le motif « séparateur bord à bord » disparaît. Ce n'est pas
  un simple transfert de padding, c'est un changement de motif. En migration
  ISO stricte, ces 4 sites gardent Paper `padding=0` et l'enfant intact —
  rien à retirer.
- **L9/L10** — cas franc : le placeholder porte 32px, aucun séparateur, aucun
  effet de bord. Si le Paper prend 24px, les 32px de `LessonsPlaceholder`
  doivent tomber (sinon 56px). **Attention** : `LessonsPlaceholder` est un
  composant partagé — le retrait doit se faire **au site d'appel**, pas dans le
  composant, sous peine de casser ses autres consommateurs.

### 5.2 — Doublement **déjà présent** dans le produit (**1 site**)

| site | padding Paper | padding enfant | cumul horizontal réel |
|---|---|---|---|
| **E3** `SectionBlock` (`EntityDetailCommon.tsx:188`) | 16px | `MuiListItem` **8px 16px** | **32px** |

C'est le seul endroit du périmètre où le padding du conteneur et celui des
lignes s'additionnent déjà aujourd'hui, avant toute migration. Deux des 61
usages de `SectionBlock` passent `disablePadding` pour l'éviter
(`GeneralVulnerabilityInfoTab.tsx:114`, `Validations.jsx:155`) — les autres
cumulent. À arbitrer séparément : c'est une correction de densité existante,
pas un effet de la migration, et la corriger **ne serait pas ISO**.

### 5.3 — Padding enfant **intrinsèque**, à ne PAS retirer (**4 sites**)

| site | padding Paper | padding enfant | pourquoi il reste |
|---|---|---|---|
| **L5** `simulations/LessonsCategories.jsx:306` | 16px | puces **4px 8px** | padding interne d'une puce, pas d'un conteneur — le retirer écrase la puce |
| **L7** `scenarios/LessonsCategories.jsx:189` | 16px | puces **4px 8px** | idem |
| **L8** `simulations/Lessons.tsx:152` | 16px | `HeroStat` **4px 32px 4px 4px** | le 32px à droite est la gouttière du séparateur `HeroStats`, structurelle |
| **E4** `DetailHero` | 16px | `HeroStat` **4px** | hors vague (§4) |

### 5.4 — Rien à faire (**3 sites**)

**L2** (graphe ApexCharts : marges internes en SVG, aucun padding DOM à
retirer — mais rien ne compense non plus si le Paper reste à 0), **E1**
`Section` et **E2** `InformationGrid` (enfants `Field` sans aucun padding ;
pour E2 l'espacement vient des `gap` de la grille, pas d'un padding).

### Récapitulatif

| famille | sites | action à la conversion |
|---|---|---|
| padding dans l'enfant, Paper à 0 | 6 (L1, L3, L4, L6, L9, L10) | **décision requise** — 4 sites à séparateurs pleine largeur (motif en jeu), 2 sites francs |
| doublement déjà présent | 1 (E3) | **arbitrage séparé** — corriger ne serait pas ISO |
| padding intrinsèque | 4 (L5, L7, L8, E4) | **ne rien retirer** |
| rien à faire | 3 (L2, E1, E2) | — |

---

## 6. Arbitrages de conversion — à appliquer au bump

Pris après le recensement §5. **À appliquer tels quels** quand la PR lib phase 0
sera mergée et le pin bumpé — pas à ré-arbitrer.

### Règle générale

> Quand le Paper porte le padding, le padding des children est **retiré** —
> **sauf quand ce padding porte un sens** : séparateur pleine largeur,
> gouttière structurelle.

### 6.1 — L1 / L3 / L4 / L6 : ISO strict, on ne transfère rien

`padding=0` sur le Paper. Les gouttières `MuiListItem` (8px 16px) **restent**.
Motif : **les séparateurs doivent continuer à toucher les bords** — c'est le cas
« ce padding porte un sens » de la règle générale.

| site | Paper après conversion | enfant |
|---|---|---|
| L1 `LessonsObjectives.jsx:26` | `padding={0}` | inchangé |
| L3 `simulations/LessonsCategories.jsx:140` | `padding={0}` | inchangé |
| L4 `simulations/LessonsCategories.jsx:203` | `padding={0}` | inchangé |
| L6 `scenarios/LessonsCategories.jsx:115` | `padding={0}` | inchangé |

### 6.2 — L9 / L10 : transfert au Paper, retrait AU SITE D'APPEL

Le padding passe sur le Paper ; les **32px de `LessonsPlaceholder` sont
retirés au site d'appel**. `LessonsPlaceholder` est un composant partagé —
**il n'est pas modifié**, sous peine d'emporter ses autres consommateurs.

| site | Paper après conversion | enfant |
|---|---|---|
| L9 `simulations/Lessons.tsx:355` | padding porté par le Paper | 32px retirés **au site d'appel** |
| L10 `scenarios/Lessons.tsx:217` | padding porté par le Paper | idem |

### 6.3 — E3 `SectionBlock` : les 32px cumulés ne sont PAS corrigés ici

Décision de densité **séparée**, à prendre à froid — hors de cette vague. La
conversion de `SectionBlock` reproduit donc le cumul existant à l'identique.

État mesuré et les deux corrections possibles, capturées en planche
(`planche-e3-densite-{dark,light}.png`, transmise hors dépôt) :

| état | Paper | gouttières de ligne | cumul horizontal | effet sur les séparateurs |
|---|---|---|---|---|
| **Actuel** — 59 des 61 usages | 16px | 16px | **32px** | rentrés de 32px |
| **Option A** — `disablePadding` | 0 | 16px | 16px | **bord à bord** |
| **Option B** — gouttières retirées | 16px | 0 | 16px | rentrés de 16px |

Les 2 usages qui passent déjà `disablePadding`
(`GeneralVulnerabilityInfoTab.tsx:114`, `Validations.jsx:155`) sont déjà en
option A. Rien n'est touché tant que la décision n'est pas prise.
