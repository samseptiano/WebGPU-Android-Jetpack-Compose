package com.samseptiano.webgpu

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private const val TAG = "WebGpuSurface"

@Composable
fun WebGpuSurface(
    modifier: Modifier = Modifier,
    imageUri: Uri?,
    generateRequested: Boolean,
    onGenerationHandled: () -> Unit
) {
    val context = LocalContext.current

    val renderer = remember(context) {
        WebGpuRenderer(context.applicationContext)
    }

    // Keep the renderer alive while the external surface exists.
    AndroidExternalSurface(
        modifier = modifier.fillMaxSize()
    ) {
        onSurface { surface, width, height ->
            try {
                withContext(Dispatchers.Default) {
                    renderer.init(
                        surface = surface,
                        width = width,
                        height = height
                    )

                    Log.d(TAG, "Surface initialized: ${width}x${height}")

                    renderer.render()

                    // Do not finish the surface coroutine immediately.
                    // Keep it alive until Android destroys the surface.
                    awaitCancellation()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WebGPU surface failed", e)
            } finally {
                withContext(Dispatchers.Default) {
                    renderer.cleanup()
                }
            }
        }
    }

    // Process the image when Generate 3D is pressed.
    LaunchedEffect(imageUri, generateRequested) {
        if (!generateRequested) return@LaunchedEffect

        try {
            if (imageUri == null) {
                Log.w(TAG, "Generate requested without an image")
                return@LaunchedEffect
            }

            Log.d(TAG, "Generating relief from image: $imageUri")

            withContext(Dispatchers.Default) {
                renderer.setImageUri(imageUri)
            }

            Log.d(TAG, "Image processing request completed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Generate 3D failed", e)
        } finally {
            onGenerationHandled()
        }
    }
}
