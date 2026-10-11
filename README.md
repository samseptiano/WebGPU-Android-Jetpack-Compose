# WebGPU Android Jetpack Compose

An experiment that turns a 2D image into a 3D-style relief on Android, using **AndroidX WebGPU**, **Kotlin**, and **Jetpack Compose**.

Bright pixels rise, dark pixels sink. The image's brightness becomes a height map, and a custom WGSL shader renders it as an isometric surface.

## How it works

1. Pick an image from the device.
2. The image is scaled down to a 64 × 64 grid.
3. Each pixel is converted to brightness (BT.601 luma, `0.0`–`1.0`).
4. The brightness values become a height map.
5. A WGSL vertex shader builds the grid mesh procedurally from `vertex_index` (no vertex buffer), and a fragment shader colors it with a height-based gradient.

## Project structure

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Compose UI: image picker, preview, **Generate 3D** button |
| `WebGpuSurface.kt` | `AndroidExternalSurface` lifecycle; forwards UI events to the renderer |
| `WebGpuRenderer.kt` | Image processing, WGSL shaders, render pipeline, drawing |

## Requirements

- Android Studio with AGP 8.13+ and Kotlin 2.2+
- `minSdk 30`, `compileSdk 36`
- A device with Vulkan support (a physical device is recommended; emulator WebGPU support varies)

Key dependencies:

```kotlin
implementation("androidx.webgpu:webgpu:1.0.0-alpha06")
implementation("io.coil-kt:coil-compose:2.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
```

## Getting started

```bash
git clone https://github.com/samseptiano/WebGPU-Android-Jetpack-Compose.git
```

Open the project in Android Studio, run it on a device, tap **Upload Image**, then **Generate 3D**.


# Screenshot

<img width="480" height="900" alt="Screenshot_20261009_161055_WebGPU Example" src="https://github.com/user-attachments/assets/13436b89-9d7d-41c4-aa0a-60567258c1ae" />

