# Android 16 CarrierConfig Write Notes

## Context

This project is based on `nrfr`, but Android 16 changed the behavior of carrier-config override writes enough that the original direct approach no longer works reliably.

The main failure observed on device was:

`overrideConfig cannot be invoked by shell`

## Root Cause

`ICarrierConfigLoader.overrideConfig(...)` can no longer be called through the old shell-flavored path on Android 16 device builds.

The direct Shizuku-based binder call from the main app process still reaches the service, but the platform rejects the caller identity.

The original workaround used instrumentation as a broker. That write path still works, but it has an important side effect:

- instrumentation interrupts the host package lifecycle
- if the broker instrumentation targets the main app package, the main UI app gets stopped or visibly exits

## What Was Tried

1. Direct binder call from the main app through Shizuku
   Result: rejected with `overrideConfig cannot be invoked by shell`

2. Delegated shell identity inside the main app process
   Result: still rejected on Android 16

3. Separate helper package with its own instrumentation code
   Result: helper could launch, but its instrumentation process did not automatically inherit the main app's Shizuku binder state

## Final Working Design

Use a two-package design:

- `com.github.countryman`
  The main Countryman UI app
- `com.github.countryman.broker`
  A helper package used only as the instrumentation target

The actual `BrokerInstrumentation` code stays in the main app package, but its `android:targetPackage` is the helper package.

That gives two benefits:

1. The broker code can still use the main app's established Shizuku access path
2. Any instrumentation-side force-stop affects the helper target package instead of the visible UI app

## Broker Permission Model

The helper package still needs its own Shizuku provider and user-granted permission path so it can be initialized and diagnosed independently.

This repo now includes:

- a dedicated `broker` module
- `Countryman Broker` init activity
- `ShizukuProvider` in the helper package
- main-app-side broker readiness checks

## User Flow

1. Install both APKs
2. Open `Countryman Broker` once
3. Grant Shizuku permission to the broker package
4. Return to `Countryman`
5. Country / carrier / reset writes can run without kicking the main UI app out

## Remaining Constraints

- The solution still depends on Shizuku
- The solution still uses instrumentation for the Android 16 fallback path
- The solution avoids exiting the main UI app, but it does not remove the need for privileged binder access

## Why Not Replace Shizuku

Replacing Shizuku would mean rebuilding the privileged binder bridge yourself:

- hidden API access
- binder lifecycle handling
- permission delegation
- service discovery
- Android-version compatibility handling

That is materially more complex than using Shizuku as infrastructure.
