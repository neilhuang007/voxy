# Fog Downport Plan

## Context

This repository is a **downport** of newer Voxy behavior onto the 1.21.1 client/rendering stack. The behavior we need to preserve on this branch is not "disable fog". It is the more specific render-flow contract that already exists in the current code:

1. let Minecraft finish resolving the frame's fog state,
2. capture that resolved state for Voxy,
3. prevent the live fog state from being applied directly to Voxy's raw distant-geometry pass when Voxy is active,
4. then reapply the captured fog during Voxy's final composite.

The important point for this downport is **semantic equivalence**, not restoration of newer internal transport types. On 1.21.1, the code already achieves the behavior by reading the resolved fog values back from `RenderSystem` at the end of `FogRenderer.setupFog(...)` and then consuming those values from the Voxy composite pass: `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:32-66`, `src/main/java/me/cortex/voxy/client/core/NormalRenderPipeline.java:91-127`.

## What the current code is doing

### Fog capture and suppression

At the tail of `FogRenderer.setupFog(...)`, Voxy currently:

- captures fog start from `RenderSystem.getShaderFogStart()`,
- captures fog end from `RenderSystem.getShaderFogEnd()`,
- captures fog color from `RenderSystem.getShaderFogColor()`,
- and, when Voxy rendering is active, pushes the live fog far away so Voxy's raw pass is not directly fogged.

That behavior is implemented in `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:32-66`.

Important details from the current logic:

- suppression only happens when Voxy rendering is enabled and a Voxy render system is present: `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:50-53`
- suppression is skipped for very short fog distances (`fogEnd < 10.0f`): `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:55`
- fluid fog is preserved when environmental fog is enabled, and only force-suppressed when environmental fog is disabled: `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:57-64`

### Final Voxy composite

The final composite pass currently reads the captured fog state from mixin-owned static accessors and uses it to drive the environmental fog uniforms and the `fogCoversAllRendering` branch in `finish(...)`: `src/main/java/me/cortex/voxy/client/core/NormalRenderPipeline.java:91-127`.

This means the current runtime contract is already:

- capture resolved fog from Minecraft,
- suppress direct fogging for the raw Voxy pass,
- reuse the captured values in the final composite.

### Iris viewport lifecycle

For the Iris path, Voxy captures per-frame viewport data at the start of `LevelRenderer.renderLevel(...)` and consumes it once during `IrisRenderingPipeline.beginLevelRendering(...)`: `src/main/java/me/cortex/voxy/client/mixin/iris/MixinLevelRenderer.java:30-58`, `src/main/java/me/cortex/voxy/client/mixin/iris/MixinIrisRenderingPipeline.java:44-52`.

The transported viewport data currently contains matrices and camera position only, via `IrisUtil.CapturedViewportParameters`: `src/main/java/me/cortex/voxy/client/core/util/IrisUtil.java:16-22`.

The viewport capture is also already guarded against stale reuse by clearing the stored parameters when shaders are inactive, when no renderer is present, and immediately after consumption: `src/main/java/me/cortex/voxy/client/mixin/iris/MixinLevelRenderer.java:40-57`, `src/main/java/me/cortex/voxy/client/mixin/iris/MixinIrisRenderingPipeline.java:46-52`.

## Why the plan must stay 1.21.1-native

The current 1.21.1 code does **not** have a consumer that expects fog parameters to travel through `Viewport` setup. `VoxyRenderSystem.setupViewport(...)` currently accepts only matrices and camera coordinates and applies projection/model-view/camera/screen-size state to the viewport: `src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java:174-218`.

So the correct downport plan is **not**:

- to recreate newer fog-parameter transport APIs,
- to push fog state through viewport setup just because newer branches once did,
- or to rewrite fog math before proving a behavioral mismatch.

The correct downport plan is to keep the existing 1.21.1 behavior, but move the fog state ownership out of the mixin statics into an explicit Voxy-owned render-frame holder.

## Solidified goal

Replace the current mixin-owned static fog transport with a small Voxy-owned frame fog state holder, while preserving the currently validated 1.21.1 behavior exactly.

In other words, this is a **refactor of ownership and lifecycle clarity**, not a change in fog semantics.

## Solidified architecture

Introduce one small fog-state holder class owned by Voxy code, likely under `me.cortex.voxy.client.core` or `me.cortex.voxy.client.core.util`.

Recommended characteristics:

- stores `fogStart`
- stores `fogEnd`
- stores `fogColor[4]`
- stores a `valid` flag
- optionally stores a debug-only `fogSuppressedForVoxy` flag if useful during testing

To avoid accidental drift from the current call sites, this holder should be treated as:

- **render-thread frame state**,
- globally reachable from both the fog mixin and the final render pipeline,
- overwritten each frame when `FogRenderer.setupFog(...)` runs,
- and never used as a reason to redesign viewport APIs.

A simple static holder class on the Voxy side is acceptable for the first implementation if that keeps behavior identical while removing ownership from `MixinFogRenderer`.

## Implementation plan

### Step 1: introduce the Voxy fog-state holder

Create a small holder class with:

- `set(start, end, color)`
- `clear()`
- read accessors
- validity query

Initial scope should stay minimal. No extra fog-shape abstractions should be introduced unless the current code proves they are necessary.

### Step 2: move fog ownership out of `MixinFogRenderer`

Update `MixinFogRenderer` so it:

- writes captured fog values into the new holder,
- stops owning `voxy$capturedFogStart`, `voxy$capturedFogEnd`, and `voxy$capturedFogColor`,
- keeps the current injection point at the tail of `FogRenderer.setupFog(...)`.

This preserves the existing 1.21.1 capture timing, which is the key runtime behavior: `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:32-48`.

### Step 3: preserve suppression behavior exactly

Keep the current suppression rules unchanged in the first implementation:

- only suppress when Voxy rendering is enabled,
- only suppress when a Voxy render system is present,
- keep the `fogEnd < 10.0f` early return,
- keep the current fluid-camera branch exactly as-is.

This is a low-risk refactor, so behavior should stay identical until runtime testing proves a specific case needs adjustment: `src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinFogRenderer.java:50-64`.

### Step 4: update `NormalRenderPipeline.finish(...)` to consume the holder

Switch `NormalRenderPipeline.finish(...)` to read fog state from the new holder instead of mixin accessors.

Keep all existing behavior initially:

- the `fogCoversAllRendering` decision
- the current `Math.abs(end - start) > 1` threshold
- the current uniform packing for fog blend values and fog color
- the current alpha-blended composite behavior

Relevant current logic: `src/main/java/me/cortex/voxy/client/core/NormalRenderPipeline.java:91-127`.

### Step 5: define explicit lifecycle rules

The holder should follow clear frame-lifecycle rules:

- a newly captured fog state overwrites the previous one,
- the holder is only considered valid after `FogRenderer.setupFog(...)` has executed for the current frame,
- if a reliable frame-boundary hook is identified later, optional clearing can be added, but it is **not required** for the first pass if overwrite semantics already match current behavior.

This keeps the plan grounded in the current code instead of inventing frame hooks before they are needed.

### Step 6: keep Iris viewport handling unchanged

Do **not** merge fog transport into Iris viewport capture in the first implementation.

Current evidence says:

- viewport capture already has clear one-shot lifecycle handling,
- viewport transport currently does not include fog,
- `setupViewport(...)` currently does not consume fog data.

So the plan should keep Iris viewport logic as-is unless runtime testing reveals an actual incompatibility: `src/main/java/me/cortex/voxy/client/core/util/IrisUtil.java:16-22`, `src/main/java/me/cortex/voxy/client/mixin/iris/MixinLevelRenderer.java:30-58`, `src/main/java/me/cortex/voxy/client/mixin/iris/MixinIrisRenderingPipeline.java:44-52`.

## Validation checklist

The validation target is to prove the refactor preserved behavior, not to chase visual changes that are outside the current contract.

1. **No active Voxy renderer**
   - fog is still captured safely,
   - suppression does not occur unexpectedly.

2. **Voxy active, no shaders**
   - raw distant geometry is not directly fogged by the live vanilla fog state,
   - final composite still fades using the captured fog envelope.

3. **Voxy active with Iris shader pack**
   - viewport capture remains one-shot per frame,
   - stale viewport data does not leak across frames,
   - final composite still follows the fog values exposed through `RenderSystem`.

4. **Fluid camera cases**
   - underwater / lava / powder snow behavior still matches the current branch semantics,
   - especially the interaction with `useEnvironmentalFog` remains unchanged.

## Non-goals

The first implementation should **not**:

- add fog parameters to `Viewport` or `VoxyRenderSystem.setupViewport(...)` without a real 1.21.1 consumer,
- restore newer-branch transport objects just because they existed elsewhere,
- change the current fog blending math in `NormalRenderPipeline.finish(...)` without evidence,
- alter Iris integration beyond preserving current lifecycle correctness.

## Summary

The codebase already has the correct **1.21.1 fog semantics** in place:

- capture resolved fog after Minecraft sets it up,
- suppress direct fogging for Voxy's raw pass when appropriate,
- reapply fog during the final Voxy composite,
- keep Iris viewport state one-shot and frame-correct.

The solidified plan is therefore to refactor **where that fog state lives**, not **how the fog behaves**.

That means introducing a Voxy-owned frame fog holder, switching the current capture and composite code to use it, and deliberately avoiding unrelated API reshaping that would create accidental drift from the current branch behavior.

