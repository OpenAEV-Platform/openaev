# Attack Path (#6647) — Besoins backend pour brancher de vraies données

> À transmettre à Laurent. Objectif : lancer un **vrai scénario** et voir des résultats
> réels dans l'onglet **Chemin d'attaque** (aujourd'hui plusieurs zones sont mockées
> ou vides côté front faute de données exposées par le back).
>
> Le front est déjà codé pour **consommer** ces champs dès qu'ils existent (structure
> prête, valeurs vides/masquées tant qu'absentes). Rien à refaire côté front après.

## État au 2026-07-21 (après merge du refactor chaining #6536 + branche attack-path)

Le merge de la branche attack-path sur `main` change beaucoup de choses : une **vraie ingestion**
(`AttackPathExecutionIngestionService`) remplace le seed synthétique, et plusieurs champs P0 sont
**désormais exposés ET résolus** côté back. Récap :

| # | Besoin | État |
|---|--------|------|
| 1 | `injectId` / `payloadId` sur `AttackPathExecutionDetailDTO` | ✅ **livré** (exposés + peuplés) |
| 2 | `detectionRemediations[]` sur le détail d'exécution | ✅ **livré** (résolu depuis le payload ; shape réelle `DetectionRemediationOutput` — front adapté le 2026-07-21) |
| 3 | Techniques ATT&CK sur l'exécution (`attackPatterns[]`) | ✅ **livré** (résolu via `InjectorContract.getAttackPatterns()`) |
| 8 | Seed réaliste | ✅ **caduc** — l'ingestion réelle existe (`AttackPathExecutionIngestionService`) |
| — | `stepTemplateId` + `executedAt` sur le nœud | ✅ **exposés** (ordre chronologique + regroupement par étape dérivables) |
| — | Fondation causale kill-chain (types **complex/primitives** + `DEPEND_ON`) | ✅ **existe dans le modèle** (#6536) — reste à l'exposer sur le DTO attack-path |
| 4 | Verdicts **par finding** | 🟡 **partiel** — verdict au niveau **exécution** (`preventionStatus`/`detectionStatus` → couleur du nœud) OK, mais `AttackPathExecutionFindingItemDTO` = `(type, value)` seulement → le `FindingDetailPanel` reste sur placeholder |
| 5 | Vraies security platforms + alertes par exécution | 🔴 **ouvert** — aucun champ `securityPlatforms[]{…, alerts[]}` dans les DTO ; le front mocke encore (CrowdStrike/Splunk + `buildAlerts`) |
| 6 | `injectorType` sur le nœud injecteur | 🟡 **partiel** — capturé à l'ingestion (`row.setInjectorType`) mais **pas remonté** sur le node DTO → `InjectorNode` devine encore le slug |
| 7 | Type de finding natif `file` | 🔴 **ouvert** (mappé sur `share`) |
| 9 | Score de chokepoint côté back | 🔴 **ouvert** (P2 ; calcul front v1) |
| 10-12 | Kill-chain : `dependsOn[]` / `consumedFindingKeys[]` + label/rang de step + états intermédiaires | 🟡 **fondation là, exposition à faire** (voir section dédiée) |

Le détail par item ci-dessous garde l'historique et le « comment » ; l'état ci-dessus fait foi.

## Contexte

Le front lit surtout deux DTO :
- `AttackPathNodeDTO` (nœuds injecteur / endpoint / finding-type / finding).
- `AttackPathExecutionDetailDTO` (détail d'une exécution, ouvert dans le drawer « Result & Terminal »).

Depuis le merge, les exécutions viennent de l'**ingestion réelle** (`AttackPathExecutionIngestionService`),
avec de vrais `contractExternalId` / `payloadId` / `injectId` → la résolution live (techniques ATT&CK,
detection remediations, lien inject) fonctionne. Le seed synthétique historique (`AttackPathSeedService`,
identifiants `contract-<injector>` etc.) reste utile en démo mais n'est plus le chemin de référence.

## Ce qu'on attend, par priorité

### P0 — débloque les features déjà construites côté front

1. ✅ **LIVRÉ.** **`injectId` (et idéalement `payloadId`) sur `AttackPathExecutionDetailDTO`**
   - Usage front : bouton **« Action details »** dans le drawer → redirige vers l'inject
     (`/…/simulations/{simId}/injects/{injectId}`). Aujourd'hui masqué faute d'id.
   - Front prêt : `onOpenInject` déjà câblé (consomme `detail.injectId`).

2. ✅ **LIVRÉ.** **Detection remediations sur le détail d'exécution**
   - Résolu par `AttackPathGraphService.executionDetail()` via `payloadMapper.toDetectionRemediationOutputs(payload.getDetectionRemediations())`. Shape réelle = `DetectionRemediationOutput` (`detection_remediation_collector` / `detection_remediation_values` / `detection_remediation_author_rule`) ; le front `ExecutionResultTerminalPanel` a été aligné sur ces noms le 2026-07-21.
   - Champ souhaité : `detectionRemediations: DetectionRemediationOutput[]` sur
     `AttackPathExecutionDetailDTO` (ou exposer `payloadId` pour que le front aille les
     chercher via l'API payload existante). Source = les detection remediations du **payload**
     de l'inject (celles de l'onglet « Remediation » d'un inject).
   - Usage front : onglet **« Remediation »** dédié dans le drawer (déjà en place), qui
     affiche par **security platform** la règle de détection. Aujourd'hui « No detection
     remediation available ».
   - Shape par entrée (réutiliser l'existant `DetectionRemediationOutput`) :
     `detection_remediation_collector` (type collecteur), `detection_remediation_values` (HTML/règle),
     `detection_remediation_author_rule`.
   - Note : feature EE (comme l'onglet inject) — garder le gating côté back.

3. ✅ **LIVRÉ** (sur l'exécution). **Techniques MITRE ATT&CK sur les nœuds injecteur / exécution**
   - `AttackPathExecutionDetailDTO.attackPatterns[]` est résolu via `InjectorContract.getAttackPatterns()`. Reste éventuellement à retirer la table statique front `attack-path-mitre.ts` au profit de ce champ.
   - Champ souhaité : `attackPatterns: AttackPatternSimple[]` (au minimum `attack_pattern_external_id`
     + `attack_pattern_name`) sur `AttackPathNodeDTO` (injecteur) et/ou `AttackPathExecutionDetailDTO`.
   - Résolution : `AttackPathExecution.contractExternalId` → `InjectorContract` →
     `injectorContract.getAttackPatterns()` → `externalId` (exactement ce que fait déjà
     `PayloadMapper` pour `payload_attack_patterns`). Batch-résoudre les contract ids distincts
     pour garder l'invariant « 2 lectures ».
   - Usage front : chips `[T1046] …` dans le drawer. Aujourd'hui **table statique front**
     (`attack-path-mitre.ts`) pour les 5 injecteurs du seed → à **remplacer** par le champ back.

4. 🟡 **PARTIEL.** **Verdicts prévention / détection / vulnérabilité par finding**
   - Le verdict **au niveau exécution** existe (`preventionStatus`/`detectionStatus`, qui pilotent la couleur du nœud). Ce qui manque : le remonter **par finding** — `AttackPathExecutionFindingItemDTO` = `(type, value)` seulement. À dériver depuis l'exécution productrice, ou ajouter les champs.
   - Champ souhaité : les verdicts réels par finding (prevention/detection/vulnerability →
     success | failed | unknown), tirés des expectations de l'exécution productrice.
   - Usage front : `FindingDetailPanel` (icônes de verdict en haut). Aujourd'hui **placeholder
     statique** (`findingExpectations`, `TODO(#6647)`).

5. 🔴 **OUVERT.** **Vraies security platforms + alertes sur le détail d'exécution**
   - Champ souhaité : par exécution, les résultats d'expectation **par plateforme** :
     `{ platformType, platformName, status (prevented/detected/…), detectedAt, alerts: [{ id, title, date }] }`.
   - Usage front : « Prevented by / Detected by » + popover d'alertes + CTA « N alerts ».
     Aujourd'hui **mocké** (CrowdStrike/Splunk en dur + `buildAlerts` = 2 titres synthétiques).
   - ⚠️ Règle métier à confirmer : **un « prevented » implique « detected »** (on ne bloque pas
     ce qu'on ne voit pas). Le front applique déjà cette implication ; à valider/garantir côté back.

### P1 — qualité des données / cohérence

6. 🟡 **PARTIEL.** **`injectorType` / slug sur le nœud injecteur** (`AttackPathNodeDTO`)
   - La donnée est **capturée à l'ingestion** (`row.setInjectorType(ctx.injectorType())`) mais **pas
     remontée** sur le node DTO du nœud injecteur → le front devine encore le slug d'icône depuis le
     label (`crackmapexec → netexec`, `TODO(#6647)` dans `InjectorNode.tsx`). Reste = mapper
     `injectorType` sur le nœud injecteur du `AttackPathNodeDTO`.

7. **Type de finding natif `file`** (« Captured Files »)
   - Aujourd'hui mappé temporairement sur `share`. Prévoir un vrai type quand l'exfil est modélisée.

8. ✅ **CADUC.** **Seed réaliste** — l'ingestion réelle (`AttackPathExecutionIngestionService`)
   remplace le seed synthétique : un vrai scénario exécuté produit désormais les vraies exécutions
   (avec `contractExternalId` / `payloadId` / `injectId` réels), donc techniques + remediations +
   lien inject se résolvent. Le seed n'est plus le seul chemin.

### P2 — scalabilité (plus tard)

9. **Score de chokepoint côté back** (`chokepointScore` sur `AttackPathNodeDTO` endpoint)
   - v1 est calculée **côté front** (somme des `findingCounts` = endpoint le plus exposé, faute de
     criticité d'asset). Pour l'échelle + une vraie pondération (betweenness + criticité d'asset via
     tags/tier), exposer un score serveur, triable/paginable.
   - Nécessite au préalable une notion de **criticité d'asset** (tags / équivalent Domain-Admin),
     inexistante aujourd'hui.

### Kill-chain intra-endpoint — la fondation causale existe désormais (chaining logic)

**Besoin métier** : modéliser une **séquence causale** d'étapes, p.ex. sur un même endpoint :
`port 445 ouvert` → `SMB validé sur 445` → `NULL session enum réussie` → `énumération des shares`
(et, entre hôtes, le pivot `host compromis → host suivant`).

> **MISE À JOUR (2026-07-21)** : la fondation bloquante (ex-point 10, `dependsOn`/`consumedFindings`)
> **existe maintenant dans le modèle** via le moteur de logique du chaining mergé sur `main`
> (#6297 event drawer, #6298 event logic flow, #4824 check conditions, #5380 expectation behaviors).
> Deux primitives couvrent le besoin :
> - **`ConditionType.DEPEND_ON`** — un step template déclare dépendre d'un autre step template
>   exécuté avant lui (`ConditionService.evaluateDependOnConditions` →
>   `existsByStepTemplateIdAndWorkflowId`). C'est le **`dependsOn`** attendu.
> - **Filter conditions typées** sur les outputs des steps précédents (`condition_key_type` :
>   `port`, `credentials`, `cve`, `share`, `username`, `vulnerability`, `sid`, `delegation`,
>   `kerberoastable_account`…), arbres AND/OR + opérateurs (`EQ`, `IN`, `IS_NOT_NULL`…). C'est le
>   **`consumedFindings`** attendu, en plus riche (opérateur, pas juste un lien).
>
> **Conséquence** : le front n'est **plus jetable** — la causalité est modélisée. Le point 10 n'est
> plus « concevoir la causalité » mais **« l'exposer sur le DTO attack-path »** : corréler
> `AttackPathExecution.stepTemplateId` → conditions du step template (`DEPEND_ON` + `condition_key_type`
> consommés) et les remonter sur `AttackPathNodeDTO`/`AttackPathExecutionDetailDTO`. Tâche de
> **mapping**, pas de modélisation. De plus, la structure de rendu DAG existe déjà côté front
> (`chaining/logic/chaining_flow/` : nodes/edges/helpers ReactFlow) — réutilisable dans `attack_path/`.

**Ce qui existe déjà (donc partiellement faisable front-only, voir plus bas)** :
- `AttackPathExecution` porte `executedAt`, `stepTemplateId`/`stepId`, `payloadName`,
  `prevention/detection status` — **exposés au front** (`AttackPathNodeDTO.executedAt`,
  `.stepTemplateId`). → l'**ordre chronologique** et le **regroupement par étape** sont dérivables.
- `AttackPathExecutionFinding` relie **exécution ↔ finding produit** → on sait quel step a produit
  le port 445 / les shares.
- La source d'une exécution peut être un **ASSET** (pas seulement un injecteur) → les **pivots /
  latéralisation** (host→host) sont déjà modélisés côté données.
- Types de finding déjà présents : `port`, `share`, `credentials`, `username`, `cve`…

**Ce qui manque côté back pour rendre une vraie kill-chain** :

10. **Dépendance causale entre étapes** — ✅ **modélisée**. Reste à **exposer**. Patch prêt à coder
    ci-dessous (décision 2026-07-21 : implémentation côté Laurent, front prêt à consommer).

    #### 🛠️ Patch back prêt à coder (pour Laurent)

    **Objectif front** : le graphe attack-path s'enrichit d'arêtes causales **finding → exécution qui
    le consomme** (motif validé : `inject → endpoint → findings(sortie) → inject suivant(entrée) → …`).
    Pour ça le front a besoin, **par nœud exécution**, des clés consommées et des dépendances.

    **Stage 1 — exposer les données brutes par exécution :**
    - Nouveau record DTO `ConsumedFindingKeyDTO(String keyType, String operator, String value)`
      (`keyType` = `PrimitiveType.name()`, `operator` = `ConditionType.name()` du leaf, `value` = `condition.getValue()`).
    - Sur **`AttackPathNodeDTO`** (nœud exécution, celui qui porte déjà `stepTemplateId`) ajouter :
      `List<String> dependsOn` et `List<ConsumedFindingKeyDTO> consumedFindingKeys`.
    - **`ConditionRepository`** : ajouter une variante **batch** anti-N+1 de `findAllLinkedToStepId` :
      `List<Condition> findAllLinkedToStepIdIn(Set<String> stepIds)` (même JPQL, `WHERE cs.step.id IN :stepIds`).
    - **`AttackPathGraphService.assemble()`** :
      1. collecter les `stepTemplateId` distincts des exécutions ;
      2. `findAllLinkedToStepIdIn(...)` en **une** lecture, grouper par `cs.step.id` ;
      3. par step template :
         - `dependsOn` = conditions où `conditionUtils.isDependOnCondition(c)` → `c.getValue()` ;
         - `consumedFindingKeys` = conditions où `conditionUtils.isFilterCondition(c)` **et**
           `c.getKeyType() != null` (les leaves ; on écarte les nœuds AND/OR sans keyType) →
           `new ConsumedFindingKeyDTO(c.getKeyType().name(), c.getType().name(), c.getValue())` ;
      4. setter les deux listes sur le nœud exécution.
    - Invariant « peu de lectures » préservé (1 requête conditions batchée en plus).

    **Stage 2 — synthèse d'arêtes causales (peut suivre) :** matcher, par exécution, ses
    `consumedFindingKeys` contre les findings **produits** (déjà connus via `AttackPathExecutionFinding`)
    sur `keyType` + opérateur, et émettre l'arête **finding → exécution**. Démarrer avec `EQ`/`IN`
    (documenter les opérateurs non gérés). *Alternative* : exposer seulement les clés brutes (stage 1)
    et laisser le front matcher `EQ`/`IN` — à trancher avec le front ; le front sait faire un premier
    matching simple si le back manque de bande passante.

    > Note front : `api-types.d.ts` étant **généré** depuis le back, ces champs n'apparaissent côté
    > front qu'après un build back. En attendant, le front est développé derrière un mock local aligné
    > sur ce contrat (mêmes noms de champs), à retirer quand les champs réels arrivent.

11. **États / résultats intermédiaires en nœuds de première classe**.
    - « SMB confirmé sur 445 » : aujourd'hui non modélisé. Soit enrichir le finding `port`
      (`service`/`protocol` = smb), soit un type `service`.
    - « NULL session réussie » : c'est un **résultat de technique** (attack-pattern / expectation),
      pas un finding. Souhaité : exposer par exécution les **techniques + verdict** (`attackPatterns[]`
      + succès/échec) pour matérialiser l'étape-technique et son issue (cf. point 3 P0).

12. **Corrélation step ↔ définition** : exposer, pour chaque `stepTemplateId`, le **label** et
    l'**ordre/priorité** de l'étape (nom lisible « NULL session enum », rang dans le scénario), pour
    étiqueter la timeline sans deviner depuis `payloadName`.

> Sans 10–12, on peut afficher une **séquence chronologique** d'étapes par endpoint (ordre = temps),
> mais pas une **causalité** garantie ni les états intermédiaires non exécutés comme étapes propres.

## Récap « ce qui reste » (pour Laurent)

Livré : `injectId`, `payloadId`, `attackPatterns[]`, `detectionRemediations[]` sur le détail
d'exécution ; ingestion réelle ; `stepTemplateId`/`executedAt` sur le nœud.

Reste à exposer :
- Sur **`AttackPathExecutionDetailDTO`** : `securityPlatforms[] { type, name, status, detectedAt, alerts[] }` (#5).
- Sur **`AttackPathNodeDTO`** (injecteur) : `injectorType` (#6, déjà en base, à mapper).
- Sur **le finding** (`AttackPathExecutionFindingItemDTO`) : verdicts prevention/detection/vulnerability (#4).
- Sémantique : garantir **prevented ⇒ detected**.
- **Kill-chain** : la causalité existe déjà (chaining `DEPEND_ON` + conditions typées / complex-primitives) —
  il suffit de l'**exposer** par exécution : `dependsOn[]` (step templates) et
  `consumedFindingKeys[]` ({keyType, operator, value}) dérivés des conditions du step template
  (via `stepTemplateId`, déjà présent), + label/rang de l'étape (#12) ; enrichir le finding `port`
  d'un `service`/`protocol` (#11) ; techniques + verdict par exécution pour les étapes-techniques.

Une fois ces derniers champs exposés, le front bascule automatiquement du mock/statique/vide vers
les vraies données (le retrait de la table statique MITRE `attack-path-mitre.ts` au profit de
`attackPatterns[]` peut se faire dès maintenant, le champ étant déjà là).
