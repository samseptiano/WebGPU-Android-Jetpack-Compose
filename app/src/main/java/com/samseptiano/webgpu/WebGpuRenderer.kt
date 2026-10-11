package com.samseptiano.webgpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.webgpu.*
import androidx.webgpu.LoadOp.Companion.Clear
import androidx.webgpu.StoreOp.Companion.Store
import androidx.webgpu.helper.WebGpu
import androidx.webgpu.helper.createWebGpu

/**
 * Image-to-3D relief renderer.
 *
 * Bright pixels produce higher areas.
 * Dark pixels produce lower areas.
 */
class WebGpuRenderer(
    private val context: Context
) {

    companion object {
        private const val TAG = "WebGpuRenderer"
        private const val GRID_SIZE = 64
        private const val VERTEX_COUNT =
            (GRID_SIZE - 1) * (GRID_SIZE - 1) * 6
    }

    private var webGpu: WebGpu? = null
    private var renderPipeline: GPURenderPipeline? = null

    // Default heightmap: flat surface.
    private var heightMap = FloatArray(
        GRID_SIZE * GRID_SIZE
    ) { 0.5f }

    suspend fun init(
        surface: Surface,
        width: Int,
        height: Int
    ) {
        require(width > 0 && height > 0) {
            "Surface dimensions must be positive"
        }

        webGpu?.close()

        val gpu = createWebGpu(surface)
        webGpu = gpu

        gpu.webgpuSurface.configure(
            GPUSurfaceConfiguration(
                gpu.device,
                width,
                height,
                TextureFormat.RGBA8Unorm
            )
        )

        createPipeline()

        Log.d(TAG, "WebGPU initialized: ${width}x${height}")
    }

    /**
     * Load an image and convert its luminance to vertex heights.
     * Call this after init() when the GPU device is ready.
     */
    fun setImageUri(uri: Uri) {
        try {
            val bitmap = context.contentResolver
                .openInputStream(uri)
                .use { stream ->
                    requireNotNull(stream) {
                        "Unable to open selected image"
                    }
                    BitmapFactory.decodeStream(stream)
                } ?: error("Unable to decode selected image")

            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                GRID_SIZE,
                GRID_SIZE,
                true
            )

            val newHeights = FloatArray(
                GRID_SIZE * GRID_SIZE
            )

            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val pixel = scaled.getPixel(x, y)

                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF

                    // Perceived brightness, normalized to 0..1.
                    val luminance = (
                            0.299f * red +
                                    0.587f * green +
                                    0.114f * blue
                            ) / 255f

                    newHeights[y * GRID_SIZE + x] = luminance
                }
            }

            heightMap = newHeights

            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()

            // Rebuild the shader using the new heightmap.
            if (webGpu != null) {
                createPipeline()
                render()
            }

            Log.d(TAG, "Image converted to heightmap")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process selected image", e)
        }
    }

    private fun createPipeline() {
        val gpu = webGpu ?: return

        val heights = heightMap.joinToString(",") {
            "%.4f".format(
                java.util.Locale.US,
                it
            )
        }

        val shaderCode = """
            const HEIGHTS: array<f32, ${GRID_SIZE * GRID_SIZE}> =
                array<f32, ${GRID_SIZE * GRID_SIZE}>(
                    $heights
                );

            struct VertexOutput {
                @builtin(position) position: vec4f,
                @location(0) height: f32,
            };

            @vertex
            fn vs_main(
                @builtin(vertex_index) vertexIndex: u32
            ) -> VertexOutput {
                let grid = ${GRID_SIZE}u;
                let cells = grid - 1u;

                let cellIndex = vertexIndex / 6u;
                let corner = vertexIndex % 6u;

                let cellX = cellIndex % cells;
                let cellY = cellIndex / cells;

                var ix = cellX;
                var iy = cellY;

                // Two triangles per grid cell.
                switch corner {
                    case 0u: {
                        ix = cellX;
                        iy = cellY;
                    }
                    case 1u: {
                        ix = cellX + 1u;
                        iy = cellY;
                    }
                    case 2u: {
                        ix = cellX;
                        iy = cellY + 1u;
                    }
                    case 3u: {
                        ix = cellX;
                        iy = cellY + 1u;
                    }
                    case 4u: {
                        ix = cellX + 1u;
                        iy = cellY;
                    }
                    default: {
                        ix = cellX + 1u;
                        iy = cellY + 1u;
                    }
                }

                let x = f32(ix) / f32(cells);
                let y = f32(iy) / f32(cells);

                let heightIndex = iy * grid + ix;
                let h = HEIGHTS[heightIndex] * 0.65;

                // Isometric-style relief projection.
                let isoX = (x - y) * 0.75;
                let isoY = (x + y) * 0.35 - h * 0.8;

                var out: VertexOutput;
                out.position = vec4f(
                    isoX,
                    -isoY + 0.30,
                    0.5,
                    1.0
                );

                out.height = HEIGHTS[heightIndex];
                return out;
            }

            @fragment
            fn fs_main(
                input: VertexOutput
            ) -> @location(0) vec4f {
                let h = clamp(input.height, 0.0, 1.0);

                let lowColor = vec3f(0.05, 0.10, 0.20);
                let midColor = vec3f(0.10, 0.55, 0.75);
                let highColor = vec3f(0.90, 0.95, 1.0);

                var color = mix(lowColor, midColor, h);

                if (h > 0.5) {
                    color = mix(
                        midColor,
                        highColor,
                        (h - 0.5) * 2.0
                    );
                }

                return vec4f(color, 1.0);
            }
        """.trimIndent()

        val shaderModule = gpu.device.createShaderModule(
            GPUShaderModuleDescriptor(
                shaderSourceWGSL = GPUShaderSourceWGSL(shaderCode)
            )
        )

        renderPipeline = gpu.device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                vertex = GPUVertexState(
                    module = shaderModule,
                    entryPoint = "vs_main"
                ),
                fragment = GPUFragmentState(
                    module = shaderModule,
                    entryPoint = "fs_main",
                    targets = arrayOf(
                        GPUColorTargetState(
                            TextureFormat.RGBA8Unorm
                        )
                    )
                ),
                primitive = GPUPrimitiveState(
                    topology = PrimitiveTopology.TriangleList,
                    frontFace = FrontFace.CCW,
                    cullMode = CullMode.None
                )
            )
        )
    }

    fun render() {
        val gpu = webGpu ?: return
        val pipeline = renderPipeline ?: return

        try {
            val surfaceTexture =
                gpu.webgpuSurface.getCurrentTexture()

            val commandEncoder =
                gpu.device.createCommandEncoder()

            val renderPass = commandEncoder.beginRenderPass(
                GPURenderPassDescriptor(
                    colorAttachments = arrayOf(
                        GPURenderPassColorAttachment(
                            GPUColor(0.025, 0.035, 0.07, 1.0),
                            surfaceTexture.texture.createView(),
                            loadOp = Clear,
                            storeOp = Store
                        )
                    )
                )
            )

            renderPass.setPipeline(pipeline)
            renderPass.draw(VERTEX_COUNT)
            renderPass.end()

            gpu.device.queue.submit(
                arrayOf(commandEncoder.finish())
            )

            gpu.webgpuSurface.present()
        } catch (e: Exception) {
            Log.e(TAG, "WebGPU render failed", e)
        }
    }

    fun cleanup() {
        webGpu?.close()
        webGpu = null
        renderPipeline = null

        Log.d(TAG, "WebGPU resources cleaned up")
    }
}