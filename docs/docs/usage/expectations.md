

#### Why use it?

Use this mode when **every member must succeed**, such as:

* mandatory training
* compliance requirements
* baseline security checks

#### How does it work?

* All targets succeed → **100**
* At least one target fails → **0**

#### Example

Group of 4 players:

* 3 succeed, 1 fails → **0 (Failed)**
* 4 succeed → **100 (Success)**

### At least one target must validate

#### Why use it?

Use this mode when **a single successful response is enough**, such as:

* SOC detection
* on-call escalation
* redundancy testing

#### How does it work?

* ≥ 1 success → **100**
* No success → **0**

#### Example

Group of 4 players:

* 1 succeeds, 3 fail → **100 (Success)**
* 4 fail → **0 (Failed)**