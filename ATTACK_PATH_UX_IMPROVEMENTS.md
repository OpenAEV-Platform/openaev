# Attack Path (#6647) — Synthèse des points d'amélioration (Design System + UX concurrentielle)

> Consolidation de deux audits (design-system interne + veille UX marché) sur la base du
> code et des captures `ap-*.png`. Complète `ATTACK_PATH_ANALYSIS.md` (capacités) et
> `ATTACK_PATH_BACKEND_REQUIREMENTS.md` (dépendances back).
> Tag source : **[DS]** = cohérence design-system interne · **[UX]** = écart vs marché.
> La spec dérivée est dans `specs/001-attack-path-ux-hardening/spec.md`.

## P0 — impact fort, front-only, à faire en premier

1. **[UX] Collision de couleur : le rouge = 3 sens simultanés** — verdict « ni prévenu ni
   détecté » (arête), badge chokepoint (flamme), badge « +N » d'endpoints cachés. On ne sait
   pas au coup d'œil ce que « rouge » veut dire. → Réserver vert/orange/rouge **strictement**
   au verdict prévention/détection ; re-skinner le badge chokepoint (forme/teinte distincte,
   pas de rouge plein) et le « +N » en pastille neutre (gris/bleu). **[DS]** confirme : même
   hue `error.main` pour « exposition » et « non détecté ».
2. **[UX] Graphe minuscule dans un grand canvas + priorisation opt-in** — pas d'auto-fit, le
   « so what » (chokepoints) n'est visible qu'après un clic. Les leaders (Tenable, MS
   Exposure Mgmt) mettent une **liste priorisée en landing**, le graphe en second. →
   Auto-`fitView` au chargement et après chaque expand ; rendre la priorisation visible
   **avant** le clic (bandeau/liste « Top chokepoints » toujours affichée, pas seulement une
   carte+popover).
3. **[DS] Accessibilité : la couleur de statut n'a aucun équivalent texte sur le graphe** — le
   principe « jamais la couleur seule » est respecté dans le feed/panneaux mais **pas** sur les
   nœuds (`AssetNode`, `EndpointClusterNode`, `FindingClusterNode`, `GroupedEdge`) : pas d'`aria-label`/
   `title`. → Ajouter le statut en texte (via `Tooltip`) sur ces nœuds/arêtes.
4. **[DS] Panneau endpoint sans bouton de fermeture** — les 3 panneaux latéraux divergent
   (finding 340px + close, exécution 400px + close, endpoint 340px **sans close**, inline ~130
   lignes). → Extraire `EndpointDetailPanel.tsx` avec header + close, aligner les 3 largeurs/headers.

## P1 — parité / cohérence visibles en démo

5. **[DS] Le drawer « findings » réimplémente un `Drawer` brut** au lieu du composant partagé
   `components/common/Drawer.tsx` (close à droite au lieu de gauche, header custom). → Migrer
   sur le Drawer partagé (utilisé par ~90 écrans).
6. **[DS] Styling incohérent** : 83 `style={{}}` inline / 0 `makeStyles`, mélangés à des `sx`
   dans le même fichier. Les *valeurs* (tokens `theme.palette`/`spacing`) sont bonnes, c'est
   l'API qui diverge de la norme. → Converger sur `sx` (ou `makeStyles` pour les motifs répétés).
7. **[UX] Légende qui se replie quand un panneau s'ouvre** — pile au moment où l'utilisateur
   interprète une couleur de verdict/chokepoint. → Garder une mini-clé 3 couleurs toujours
   visible ; ne replier que le glossaire des formes.
8. **[UX] Pas de heatmap ATT&CK agrégée** — seulement un chip par injecteur. Les blue teams
   attendent la heatmap (Tenable a un onglet dédié ; Picus/AttackIQ en font l'artefact
   signature). → Bandeau agrégé « N techniques observées, M non détectées » au-dessus du graphe
   (front-only, `attack-path-mitre.ts` suffit), extensible en mini-matrice plus tard.
9. **[UX] Preuve texte-only** — pas de fil d'Ariane du chemin dans le panneau exécution. →
   Ajouter un breadcrumb « injecteur → endpoint → finding » (donnée déjà résolue en vue focus).
   Capture d'écran de preuve (façon NodeZero) = pari plus gros, dépend du back.
10. **[DS] Verdicts couleur = ré-implémentation privée** de `utils/Colors.ts`/`statusUtils.ts`.
    → Documenter le parallèle, ou converger (mapper le `GREEN/ORANGE/RED` back sur le
    vocabulaire `prevented/detected/failed` partagé).
11. **[DS] Sémantique de clic à 4 comportements** (finding→panel, injecteur→drawer,
    cluster→expand, endpoint→panneau) non signalée dans l'UI. → Indice par forme dans la
    légende / au survol.

## P2 — complétude / plus gros paris

12. **[UX] Pas de vue table/liste** (ni export) alternative au graphe. BloodHound a ajouté une
    Table View justement parce qu'un graphe est un mauvais artefact de revue/partage ; Tenable
    est **table-first**. → Vue table « chokepoints + findings » (réutilise la donnée des drawers)
    = résout **à la fois** l'export (N10) et la lisibilité à grande échelle (N6).
13. **[UX] Pas de recherche/filtre global** (5 cartes fixes seulement). → Barre de filtres
    (injecteur, sévérité, OS/plateforme) + recherche libre ; cadrer « liste/table d'abord ».
14. **[DS] Badges de nœud incohérents** (chokepoint 34×30 vs « +N » 22×22, rayons différents).
    → Échelle de badge partagée (small/medium, `Chip`-like).
15. **[DS] Dates ISO brutes** dans le popover d'alertes (`2026-01-01T20:02:57Z`). → Passer par
    `fldt` (fix 1 ligne).
16. **[DS] Duplication** : cartes de synthèse (×5) + carte chokepoints ré-utilisent le même bloc
    `sx` → extraire `<SummaryCard>` ; 2 patterns « image cassée → icône fallback » (`InjectorNode`,
    `PlatformLogo`) → 1 helper `<ImageWithFallback>`.
17. **[DS] Light / empty / loading** non vérifiés en thème clair (toutes les captures sont dark).
    → Passe rapide light-mode + états vides « intentionnels » (cf. MS « empty page is expected »).
18. **[DS/known debt] i18n** mixte FR/EN visible (`RÉSULTAT` au-dessus de `Prevented by`). →
    `yarn auto-translation:all` avant GA.

## Forces déjà en place (à préserver)
Panneau de preuve in-flow ancré au nœud (≠ modale) ; clustering + drill-down avec hub dédupliqué ;
focus bidirectionnel sans renavigation ; badges chokepoint intégrés au graphe (≈ MS) ; carte
« Top chokepoints » (≈ Tenable/MS) ; légende explicite ; minimap >40 nœuds ; états loading/erreur
cohérents (`Loader`, `Alert`).

## Quick wins (faible effort, fort impact — front-only)
Auto-fit du graphe · re-skin badges hors rouge · mini-clé couleur toujours visible · breadcrumb
de chemin · bandeau ATT&CK agrégé · close + extraction du panneau endpoint · `fldt` sur les dates.

## Plus gros paris (différenciants)
Vue table/export · mini-heatmap ATT&CK interactive · landing « liste priorisée d'abord »
(pour très grands graphes) · narratif pas-à-pas + captures de preuve (dépend du back).
