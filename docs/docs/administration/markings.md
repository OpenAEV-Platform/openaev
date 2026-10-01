# Markings

Markings are sensitivity labels you can attach to Assets to control who is allowed to see them, on top of OpenAEV's
regular capabilities and grants. By default, OpenAEV seeds every tenant with five levels of the Traffic Light Protocol
(TLP), a common standard for sharing sensitive information, and you can define your own marking types if TLP does not
fit your organization.

## Why use it?

Capabilities and grants (see [Users and RBAC](users-and-rbac.md)) control *what* a user can do and *which* Simulations,
Scenarios, or Organizations they can reach. Markings add a separate, independent layer: they control who can even
**see** a given Asset, based on how sensitive it is, regardless of the capabilities or grants that user otherwise holds.

This is useful when:

- Some Endpoints, identities, or AI targets are more sensitive than others and should stay invisible to most users even
  though they belong to the same tenant.
- You need to share the platform across teams with different sensitivity levels without splitting them into separate
  tenants.
- You want assigning or removing a label to require the same level of care as the label itself -- see
  [Assign markings to an Asset](#assign-markings-to-an-asset).

## How markings work

A marking definition is a single sensitivity level, for example `TLP:AMBER`. The seeded TLP levels, from least to most
restrictive, are:

| Definition         | Color     |
|---------------------|-----------|
| `TLP:CLEAR`         | `#E6E7E8` |
| `TLP:GREEN`         | `#4CAF50` |
| `TLP:AMBER`         | `#FFB300` |
| `TLP:AMBER+STRICT`  | `#FF8F00` |
| `TLP:RED`           | `#E53935` |

!!! note "Levels are cumulative"

    Levels within the same type are ordered, and holding one implies holding every less restrictive level of that
    type. A group or user cleared for `TLP:AMBER` can also see and assign `TLP:GREEN` and `TLP:CLEAR`.

An Asset can carry zero, one, or several markings:

- An Asset with **no marking** is visible to everyone who could normally see it.
- An Asset with **one or more markings** is visible only to users who hold **every** marking it carries. Missing even
  one of them hides the whole Asset, the same way as if it did not exist -- including from administrators without the
  matching marking.

!!! tip "Bypass"

    Users with the `Bypass` capability hold every marking automatically and are never restricted by this mechanism.

## Manage marking definitions

Go to **Settings > Security > Marking definitions** to see every marking defined in your tenant.

1. Click **Add a marking definition**.
2. Enter a **Type** (e.g. `TLP`), a **Definition** (e.g. `TLP:AMBER`), a **Color**, and an **Order**.
3. Save.

The **Order** decides what a definition implies: a higher order is more restrictive and implies every lower order of
the same type.

The five seeded `TLP` levels are protected and cannot be edited or deleted. For a definition you create yourself, the
**Type** is fixed once saved, but the **Definition**, **Color**, and **Order** can still be changed, and the
definition can be deleted.

!!! warning "Changing the order affects everyone immediately"

    Changing a definition's **Order** changes what every group holding that type implies platform-wide, for every
    user, the moment you save -- not just for new grants.

!!! warning "Deleting removes the marking everywhere"

    Deleting a marking definition removes it from every Asset and group currently carrying it. This happens
    immediately and cannot be undone.

## Grant markings to a group

Holding a marking is a property of a **group**, the same way roles are. To grant it:

1. Go to **Settings > Security > Groups**.
2. Open the group's menu and click **Manage markings**.
3. Select the highest level you want the group cleared for, per type. Every lower level of that type is selected
   automatically.
4. Save.

Every member of the group immediately gains the clearance to see and assign the markings it holds.

!!! warning "You can only grant what you hold"

    Just like capabilities (see [Delegating capabilities](users-and-rbac.md#delegating-capabilities)), you cannot
    grant a group a marking you do not hold yourself. This prevents a user from manufacturing broader access than
    their own by routing it through a group.

## Assign markings to an Asset

Open any Asset for editing (an Endpoint, an AI target, or any other Asset category) and use the **Markings** field:

1. Select the markings this Asset should carry. The picker only offers markings you are yourself cleared to assign --
   the same rule as granting a group.
2. Save.

Markings are replaced wholesale: whatever is selected when you save becomes the Asset's complete marking set,
including removing any marking that is not re-selected. Removing a marking widens who can see the Asset, so make sure
you mean it before saving.

Assigned markings also show up:

- In the **Markings** column of the Endpoints list.
- In the **Markings** field of the Asset Information panel, on the Asset's detail page.

## Example

> Scenario: a managed security provider runs OpenAEV for several clients from a single tenant.

- The **Client A analysts** group is granted `TLP:AMBER`.
- The **Client B analysts** group is granted `TLP:AMBER` as well, for a separate, unrelated engagement.
- Client A's most sensitive Endpoints are marked `TLP:AMBER`.

Both groups can see `TLP:AMBER` Endpoints, so marking alone does not separate Client A's Endpoints from Client B's
analysts: markings control *how sensitive* an Asset is, not *which team* it belongs to. To actually keep the two
clients apart, give each client's Endpoints a distinct marking type instead, for example a custom `CLIENT:A` /
`CLIENT:B` type with one level each, and grant each analyst group only its own client's type.

## What's next?

- [Users and RBAC](users-and-rbac.md) -- Understand capabilities and grants, the other two layers of access control
- [Taxonomies](taxonomies.md) -- Tag and classify Assets the rest of the platform searches and filters on
- [Assets](../usage/build/assets.md) -- Manage Endpoints, Asset groups, and Security platforms
