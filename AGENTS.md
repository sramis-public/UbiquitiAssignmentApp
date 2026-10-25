# Ubiquiti Android take-home assignment

## Scope and sources

Build a small product overview app with one list screen and one details screen.
The assignment source of truth is **UniFi Mobile Test Assignment (Android).pdf**;
apply agreed project decisions without presenting them as assignment requirements.
Use the [supplied Figma design](https://www.figma.com/design/50CoCBulIh6LcLck5ihXiW/Ubiquiti-Mobile-Test-Assignment)
as the visual reference after inspecting it. Inspect the existing project, PDF,
design, and actual UIDB JSON before implementation. Do not scaffold or implement
until requested.

## Required behavior

- Use Kotlin and Jetpack Compose.
- Show product images and model names. Provide **All**, **Cameras**, and **IoT**
  tabs that update the list immediately.
- Open the selected product's details with its name and image. Additional
  attributes are optional; use only attributes supported by the data.
- Handle loading, empty, error, recovery, and missing or failed image states.
- Cache catalog data. The assignment allows memory or disk caching; full offline
  support is not mandated. Document implemented cache and refresh behavior.

## Agreed stack

These are project decisions, not additional assignment requirements:

- Kotlin, Jetpack Compose, and coroutines.
- Retrofit + OkHttp for networking; kotlinx.serialization for JSON parsing.
- Room for catalog persistence; Coil for image loading.
- ViewModel + StateFlow for screen state, collected with lifecycle awareness.

Keep the structure small: a repository boundary for data access, ViewModels for
state handling, and composables for rendering and user interaction. Avoid
unnecessary modules, layers, frameworks, and unrelated features. Justify any
additional dependencies or platform constraints before adopting them.

## Data and engineering boundaries

Use [public UIDB JSON](https://static.ui.com/fingerprint/ui/public.json).
Inspect its structure before defining models or category predicates. Use `id`
for stable identity, `product.name` for display, `line` for category mapping,
`images.default` for images, and `shortnames` only if needed.

The PDF supplies no explicit Cameras/IoT mapping. Verify the meaning of `line`
against available evidence and document the chosen mapping and unresolved
ambiguity. Do not invent attributes or assume optional fields exist.

Construct image URLs in the assignment's form, choosing a suitable width:

```text
https://images.svc.ui.com/?u=https%3A%2F%2Fstatic.ui.com%2Ffingerprint%2Fui%2Fimages%2F${id}%2Fdefault%2F${images.default}.png&w=${size}&q=75
```

- Keep networking and persistence outside composables and off the main thread.
- Use structured concurrency and explicit, preferably immutable screen state.
- Parse only needed fields, tolerate unknown fields, and handle missing data deliberately.
- Filter loaded data without redundant requests; preserve stable identity and
  predictable details/back navigation.
- Make cache ownership, reuse, refresh, and failure behavior explicit and testable.
- Match inspected Figma typography, spacing, colors, and imagery. Provide readable
  text, suitable touch targets, and image descriptions.
- Keep changes focused and preserve unrelated work. Separate verified facts from assumptions.

## Validation and delivery

- Add deterministic unit tests for parsing and missing fields, category mapping,
  image URLs, cache reuse/failure, and relevant screen-state transitions.
- Add focused Compose/navigation tests where useful for tab filtering and opening
  the correct details. Prefer fixtures over live-network tests.
- Run available project build, unit-test, and Android lint tasks. Report exact
  checks, outcomes, and environment limitations; never claim an unrun check passed.
- When a device/emulator is available, verify all tabs, details/back navigation,
  loading/empty/error recovery, image failures, caching, and both screens against Figma.
- Deliver full source and build configuration with a short README explaining how
  to run, assumptions/tradeoffs, category mapping, cache limits, libraries and
  reasons, validation performed, and remaining limitations.

## Optional local guidance

If `AGENT_LOCAL.md` exists at the project root, read it for supplementary local
workflow preferences. If absent, continue normally; it is not required to build,
test, or contribute. Local guidance must not override the assignment or agreed
project decisions. Keep that file untracked using the root `.gitignore` rule.
