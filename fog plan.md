# Fog Downport Plan

## Goal

Bring the **upstream fog intent** onto the 1.21.1 branch in a way that is:

- achievable on the current 1.21.1 rendering stack,
- low-risk,
- easy to validate step by step,
- and careful not to break existing 1.21.1 rendering behavior.

The target behavior is **not** "disable fog".

The target behavior is:

1. let Minecraft finish computing the frame's fog,
2. preserve the useful fog state Voxy wants to keep,
3. stop the live vanilla terrain-distance fog from directly fogging Voxy's raw distant pass,
4. then reapply the preserved fog during Voxy's final composite.

This matches the verified upstream intent:

- preserve atmospheric/environmental fog semantics where appropriate,
- suppress the direct raw-pass distance/border fog contribution that conflicts with distant rendering,
- keep final fog application under Voxy's control.

## What was verified

### Verified upstream intent

Upstream branches show a consistent design direction:

- when Voxy is active, the raw vanilla fog envelope used for terrain-distance fading is pushed far away,
- environmental fog is treated separately,
- the final render path consumes preserved fog data later.

In newer upstream branches this fog data travels through viewport/fog-parameter transport, but that transport shape is **not** present on 1.21.1 and should not be recreated unless it is actually needed.

### Verified current 1.21.1 reality

The current 1.21.1 branch does **not** yet implement the full capture-and-reapply contract.

What it currently does is simpler:

- at the tail of `FogRenderer.setupFog(...)`, when Voxy is active,
- it checks the resolved `RenderSystem` fog end,
- skips very short fog,
- then pushes fog start and end far away.

That means the current branch is already doing the **suppression** part, but not yet the full **capture + final composite reuse** part.

## Design constraints for 1.21.1

To avoid breakage on 1.21.1, this plan must respect the branch's actual architecture.

### Constraint 1: do not redesign viewport APIs first

On this branch:

- `VoxyRenderSystem.setupViewport(...)` accepts matrices and camera only,
- `IrisUtil.CapturedViewportParameters` carries matrices and camera only,
- `Viewport` does not currently own fog parameters.

So the safest first implementation is **not** to import newer-branch fog transport APIs into viewport setup.

### Constraint 2: keep the current fog hook timing

The current mixin runs at the tail of `FogRenderer.setupFog(...)`. That timing is valuable because Minecraft has already resolved the fog state for the frame by then.

That means the safest 1.21.1 implementation should:

- keep capture at the tail of `setupFog(...)`,
- read the resolved fog values from `RenderSystem`,
- suppress live fog only after capture,
- and reuse the captured values later in Voxy's composite.

### Constraint 3: preserve the current suppression guardrails

The current branch already has several safety guardrails that should remain unchanged in the first pass:

- only act for terrain fog,
- only act when Voxy rendering is enabled,
- only act when a Voxy render system is present,
- skip very short fog distances.

Those guardrails reduce the chance of breaking fluid fog, special close-range fog, menu rendering, or unrelated vanilla paths.

## Step-by-step implementation plan

This implementation should be done in small, verifiable steps.

---

## Step 1: introduce a Voxy-owned frame fog holder

Create a small Voxy-side holder class for the current frame's fog state.

Recommended contents:

- `float fogStart`
- `float fogEnd`
- `float[] fogColor` or four explicit color components
- `boolean valid`
- optional debug flag like `fogWasSuppressed`

Recommended operations:

- `set(start, end, r, g, b, a)`
- `clear()`
- getters/accessors
- `isValid()`

### Why this is safe

- it keeps fog ownership in Voxy code,
- it avoids expanding viewport APIs,
- it lets the mixin and pipeline communicate without depending on mixin statics,
- it is a pure refactor/setup step and should not change rendering yet.

### Completion criteria

- project compiles,
- no rendering behavior changed yet,
- no existing code path depends on fog holder contents yet.

---

## Step 2: capture the resolved fog before suppression

Update the existing `MixinFogRenderer` logic so that, at the tail of `FogRenderer.setupFog(...)`, it first captures:

- `RenderSystem.getShaderFogStart()`
- `RenderSystem.getShaderFogEnd()`
- `RenderSystem.getShaderFogColor()`

and writes those values into the new fog holder.

### Important rule

Capture must happen **before** any suppression is applied.

### Why this is safe

- it uses the same hook timing already present on 1.21.1,
- it reads already-resolved fog state instead of trying to reconstruct fog math,
- it does not require new dependencies on Sodium/Iris fog transport types.

### Completion criteria

- project compiles,
- fog holder contains per-frame data after `setupFog(...)` runs,
- no visual behavior should change yet if the captured data is not consumed anywhere.

---

## Step 3: keep suppression rules unchanged in the first pass

After capture, preserve the current suppression behavior exactly unless a specific bug is proven.

That means keeping these rules as-is:

- only suppress for terrain fog,
- only suppress when Voxy rendering is enabled,
- only suppress when a Voxy render system exists,
- if fog end is very short, return early,
- otherwise push fog start/end far away.

### Why this matters

This keeps the first real behavioral change as small as possible.
The branch already depends on this suppression logic, so rewriting it too early would increase risk.

### What not to do yet

Do **not** attempt to import the exact newer-branch `FogData` or viewport fog transport model into 1.21.1 during this step.

### Completion criteria

- current suppression behavior still works,
- no regressions in normal terrain rendering,
- very short fog cases still bypass suppression.

---

## Step 4: make the final Voxy composite consume captured fog

Update `NormalRenderPipeline.finish(...)` so that it reads the captured fog state from the new holder and uses it to drive the final composite.

The first implementation should stay conservative.

### Recommended first-pass behavior

- only consume the holder if it is valid,
- compute fog blend values from captured `start` and `end`,
- pass fog color from the holder into the final shader uniforms,
- preserve the existing alpha-blended composite path.

### Why this is the key step

This is the step that restores upstream intent on 1.21.1:

- raw Voxy geometry is no longer directly fogged by live vanilla distance fog,
- but the final image still receives the preserved fog envelope.

### Safety rule

If the captured fog state is invalid for a frame, the pipeline should fall back safely rather than assuming fog data exists.

Possible safe first-pass fallback:

- skip fog-specific composite uniforms when the holder is invalid.

### Completion criteria

- project compiles,
- Voxy still renders normally,
- final composite uses captured fog when available,
- no crash or undefined behavior when fog holder is invalid.

---

## Step 5: add explicit lifecycle rules

Define clear rules for when the holder is considered current.

Initial lifecycle rules:

- the holder is overwritten each frame when `FogRenderer.setupFog(...)` runs,
- the latest captured values are the active frame values,
- `valid` becomes true on successful capture,
- optional clearing can happen at a proven frame boundary later, but is not required for the first implementation if overwrite semantics are enough.

### Why this is safe

It avoids inventing a new frame hook before the current code proves one is needed.

### Completion criteria

- captured state is never partly updated,
- the holder cannot expose mixed old/new values,
- there is a clear fallback path if capture did not occur for a frame.

---

## Step 6: keep Iris viewport handling unchanged for the first pass

Do not merge fog transport into Iris viewport capture yet.

Current 1.21.1 evidence says:

- Iris viewport capture is already separate,
- viewport transport currently carries matrices and camera only,
- the current branch has no 1.21.1 viewport fog consumer.

### Why this is safe

Keeping Iris viewport handling unchanged minimizes risk and keeps the downport focused on the real requirement.

### Completion criteria

- no viewport API changes are required for the first implementation,
- no new Iris-only fog path is introduced unless testing proves it necessary.

---

## Step 7: validate behavior in controlled scenarios

Validation should happen in the following order.

### Scenario A: Voxy disabled or unavailable

Expected result:

- fog behaves exactly like normal vanilla behavior,
- capture may occur harmlessly, but suppression must not occur.

### Scenario B: Voxy enabled, no shaders

Expected result:

- raw distant geometry is not directly cut off by live vanilla distance/border fog,
- final composite still applies the captured fog envelope,
- the scene keeps the intended atmospheric fade instead of looking fully fogless.

### Scenario C: short fog / special close fog cases

Expected result:

- the existing short-fog bypass still prevents accidental suppression,
- close-range special fog does not disappear unexpectedly.

### Scenario D: Nether / End / strong environmental fog conditions

Expected result:

- atmospheric fog feel is preserved in the final image,
- Voxy does not look like it globally disabled fog,
- only the unwanted direct raw-pass border/distance fog effect is removed.

### Scenario E: Iris enabled

Expected result:

- no stale viewport state is introduced,
- no new crash path appears,
- fog capture still reflects the resolved render-thread state on the frame.

---

## Things deliberately out of scope for the first pass

The first safe implementation should **not** do any of the following unless later testing proves they are necessary:

- rewrite 1.21.1 around newer upstream viewport fog transport,
- add fog parameters to `Viewport` immediately,
- change Iris viewport data transport shape,
- redesign fog math beyond using the resolved captured values,
- change suppression heuristics beyond today's guards,
- attempt a large shader rewrite before proving the minimal path works.

## Implementation order to minimize breakage

The recommended order of work is:

1. add the Voxy fog holder,
2. wire capture into the existing fog mixin,
3. keep current suppression rules unchanged,
4. teach the final composite to consume the captured fog,
5. validate normal rendering and special fog cases,
6. only then consider any optional cleanup or API reshaping.

This order ensures every step is small, testable, and reversible.

## Definition of done

This downport is complete when all of the following are true:

- Voxy no longer lets live vanilla terrain-distance fog directly fog the raw distant pass,
- the final composite reuses captured fog values from Minecraft's resolved fog state,
- Nether/End-style atmospheric fog feel is preserved instead of globally removed,
- the current 1.21.1 suppression guardrails still hold,
- no viewport or Iris API redesign was required to achieve the result,
- the branch remains stable in no-shader and shader-enabled play.

## Summary

The safest achievable 1.21.1 plan is:

- keep the existing `setupFog(...)` tail hook,
- capture the resolved fog from `RenderSystem`,
- preserve the current suppression rules,
- move fog state ownership into a small Voxy holder,
- and consume that captured fog during the final Voxy composite.

That delivers the upstream intent without forcing 1.21.1 to imitate newer branch internals that do not exist on this branch.
