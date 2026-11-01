package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Bitmap
import android.view.Surface
import androidx.webgpu.BufferUsage
import androidx.webgpu.FilterMode
import androidx.webgpu.GPUBindGroup
import androidx.webgpu.GPUBindGroupDescriptor
import androidx.webgpu.GPUBindGroupEntry
import androidx.webgpu.GPUBindGroupLayout
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUColor
import androidx.webgpu.GPUColorTargetState
import androidx.webgpu.GPUCommandBuffer
import androidx.webgpu.GPUCommandEncoder
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPURenderPassEncoder
import androidx.webgpu.GPURenderPipeline
import androidx.webgpu.GPURenderPipelineDescriptor
import androidx.webgpu.GPUSampler
import androidx.webgpu.GPUSamplerDescriptor
import androidx.webgpu.GPUShaderModule
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import androidx.webgpu.GPUSurfaceCapabilities
import androidx.webgpu.GPUSurfaceConfiguration
import androidx.webgpu.GPUSurfaceTexture
import androidx.webgpu.GPUTexelCopyBufferLayout
import androidx.webgpu.GPUTexelCopyTextureInfo
import androidx.webgpu.GPUTexture
import androidx.webgpu.GPUTextureDescriptor
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.StoreOp
import androidx.webgpu.SurfaceGetCurrentTextureStatus
import androidx.webgpu.TextureFormat
import androidx.webgpu.TextureUsage
import androidx.webgpu.helper.WebGpu
import androidx.webgpu.helper.createWebGpu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

private const val BYTES_PER_PIXEL = 4

/** Dawn enforces the WebGPU spec's 256-byte copy stride for texture uploads. */
private const val TEXTURE_COPY_ALIGNMENT = 256

/** vec2f scale + vec2f offset, both in NDC units. */
private const val PARAMS_BUFFER_SIZE = 16L

/** Fullscreen-quad pipeline: scales the page texture with the uniform's NDC transform. */
private val VIEWER_WGSL = """
    struct Params {
        scale: vec2f,
        offset: vec2f,
    };

    @group(0) @binding(0) var<uniform> params: Params;
    @group(0) @binding(1) var pageSampler: sampler;
    @group(0) @binding(2) var pageTexture: texture_2d<f32>;

    struct VertexOut {
        @builtin(position) position: vec4f,
        @location(0) uv: vec2f,
    };

    @vertex
    fn vsMain(@builtin(vertex_index) vertexIndex: u32) -> VertexOut {
        var quad = array<vec2f, 6>(
            vec2f(-1.0, -1.0),
            vec2f(1.0, -1.0),
            vec2f(-1.0, 1.0),
            vec2f(-1.0, 1.0),
            vec2f(1.0, -1.0),
            vec2f(1.0, 1.0),
        );
        let unit = quad[vertexIndex];
        var out: VertexOut;
        out.position = vec4f(unit * params.scale + params.offset, 0.0, 1.0);
        // Decoder frames are top-first while NDC is y-up: flip v to keep pages upright.
        out.uv = vec2f(unit.x * 0.5 + 0.5, 0.5 - unit.y * 0.5);
        return out;
    }

    @fragment
    fn fsMain(in: VertexOut) -> @location(0) vec4f {
        return textureSample(pageTexture, pageSampler, in.uv);
    }
""".trimIndent()

/**
 * Renders reader pages onto an Android [Surface] with androidx.webgpu (WebGPU migration, phase 4).
 *
 * Owns the whole GPU environment behind one surface: instance/adapter/device/surface (through the
 * [WebGpu] helper), a single WGSL pipeline that draws one page texture aspect-fitted into the
 * surface, and the page textures created from decoder frames ([createFromImage]) or bitmaps
 * ([createFromBitmap]). Implements the same [GpuImageBackend] seam the HQ viewer uses, so the
 * viewer keeps a single image lifecycle contract across backends.
 *
 * Threading: Dawn is not thread-safe, so every GPU call runs inside [WebGpu.execute] on its
 * single-threaded dispatcher. [stop] blocks until in-flight frames have drained, which makes it
 * safe to call from `SurfaceHolder.Callback.surfaceDestroyed` before Android releases the surface.
 */
class AndroidxWebGpuSurfaceRenderer : GpuImageBackend<AndroidxWebGpuSurfaceRenderer.PageImage> {

    /** A page image living in GPU memory; [destroyed] flips when [destroy] releases the texture. */
    class PageImage internal constructor(
        internal val texture: GPUTexture,
        val width: Int,
        val height: Int,
    ) {
        @Volatile
        internal var destroyed = false
    }

    private val lock = Any()

    private var webgpu: WebGpu? = null
    private var pipeline: GPURenderPipeline? = null
    private var bindGroupLayout: GPUBindGroupLayout? = null
    private var sampler: GPUSampler? = null
    private var paramsBuffer: GPUBuffer? = null
    private var shaderModule: GPUShaderModule? = null

    /** Set as soon as a stop begins; kills uploads and frames that race the teardown. */
    @Volatile
    private var stopped = true

    // Only touched from the WebGPU dispatcher thread.
    private var surfaceFormat: Int = TextureFormat.RGBA8Unorm
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    /** Images alive on the current environment; all released by [stop] (they die with the device). */
    private val liveImages = LinkedHashSet<PageImage>()

    private var renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

    /** The page the next frame draws; null clears the surface. */
    @Volatile
    private var currentPage: PageImage? = null

    val isRunning: Boolean
        get() = synchronized(lock) { webgpu != null }

    /** Sets the page drawn by the next frame (`null` clears the surface). */
    fun setPage(image: PageImage?) {
        currentPage = image
    }

    /**
     * Brings up the GPU environment bound to [surface], configured for [width] x [height], and
     * creates the pipeline the frames use. Replaces any environment still running.
     */
    suspend fun start(surface: Surface, width: Int, height: Int) {
        if (isRunning) stop()
        val environment = createWebGpu(surface = surface)
        try {
            environment.execute {
                surfaceFormat = pickSurfaceFormat(environment.webgpuSurface.getCapabilities(environment.adapter))
                surfaceWidth = width.coerceAtLeast(1)
                surfaceHeight = height.coerceAtLeast(1)
                configureSurface(environment, surfaceWidth, surfaceHeight)

                // Every object below is tracked in a field and released by releaseResources(),
                // on both the success path (stop) and this start's failure path.
                val shader = environment.device.createShaderModule(
                    GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(VIEWER_WGSL)),
                )
                shaderModule = shader
                val pipe: GPURenderPipeline = environment.device.createRenderPipeline(
                    GPURenderPipelineDescriptor(
                        vertex = GPUVertexState(module = shader, entryPoint = "vsMain"),
                        fragment = GPUFragmentState(
                            module = shader,
                            entryPoint = "fsMain",
                            targets = arrayOf(GPUColorTargetState(format = surfaceFormat)),
                        ),
                    ),
                )
                pipeline = pipe
                bindGroupLayout = pipe.getBindGroupLayout(0)
                sampler = environment.device.createSampler(
                    GPUSamplerDescriptor(magFilter = FilterMode.Linear, minFilter = FilterMode.Linear),
                )
                paramsBuffer = environment.device.createBuffer(
                    GPUBufferDescriptor(
                        usage = BufferUsage.Uniform or BufferUsage.CopyDst,
                        size = PARAMS_BUFFER_SIZE,
                    ),
                )
            }
        } catch (e: Exception) {
            runBlocking { runCatching { environment.execute { releaseResources() } } }
            environment.close()
            throw e
        }
        synchronized(lock) {
            stopped = false
            webgpu = environment
            renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }

    /** Reconfigures the surface for a new size and redraws the current page. */
    fun resize(width: Int, height: Int) {
        val environment = synchronized(lock) { webgpu } ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        renderScope.launch {
            renderMutex.withLock {
                environment.execute {
                    if (stopped) return@execute
                    surfaceWidth = w
                    surfaceHeight = h
                    configureSurface(environment, w, h)
                    drawFrame(environment)
                }
            }
        }
    }

    /** Schedules a frame of [currentPage] (or a clear when none) onto the surface. */
    fun requestRender() {
        val environment = synchronized(lock) { webgpu } ?: return
        renderScope.launch {
            renderMutex.withLock {
                environment.execute {
                    if (stopped) return@execute
                    drawFrame(environment)
                }
            }
        }
    }

    /**
     * Tears the environment down synchronously: queued frames drain on the single dispatcher
     * thread first, then every GPU resource is released. Idempotent; safe to call from the main
     * thread (it only returns once nothing touches the GPU or the surface anymore).
     */
    fun stop() {
        val environment: WebGpu
        val toRelease: List<PageImage>
        synchronized(lock) {
            val current = webgpu ?: return
            environment = current
            webgpu = null
            stopped = true
            toRelease = liveImages.toList()
            liveImages.clear()
            toRelease.forEach { it.destroyed = true }
            renderScope.cancel()
            renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        runBlocking {
            environment.execute {
                toRelease.forEach { image -> runCatching { image.texture.close() } }
                releaseResources()
            }
        }
        // Note: WebGpu.close() deliberately skips device.close() (upstream b/428866400); the
        // per-image and pipeline resources above are still released explicitly.
        environment.close()
    }

    /** Closes every non-environment GPU object; runs on the WebGPU dispatcher. */
    private fun releaseResources() {
        paramsBuffer?.let { runCatching { it.close() } }
        sampler?.let { runCatching { it.close() } }
        bindGroupLayout?.let { runCatching { it.close() } }
        pipeline?.let { runCatching { it.close() } }
        shaderModule?.let { runCatching { it.close() } }
        paramsBuffer = null
        sampler = null
        bindGroupLayout = null
        pipeline = null
        shaderModule = null
    }

    private fun configureSurface(environment: WebGpu, width: Int, height: Int) {
        environment.webgpuSurface.configure(
            GPUSurfaceConfiguration(
                device = environment.device,
                width = width,
                height = height,
                format = surfaceFormat,
                usage = TextureUsage.RenderAttachment,
            ),
        )
    }

    /** Picks a plain 8-bit RGBA/BGRA format from what the surface supports. */
    private fun pickSurfaceFormat(capabilities: GPUSurfaceCapabilities): Int {
        val formats = capabilities.formats
        return formats.firstOrNull { it == TextureFormat.RGBA8Unorm }
            ?: formats.firstOrNull { it == TextureFormat.BGRA8Unorm }
            ?: formats.firstOrNull { it == TextureFormat.RGBA8UnormSrgb }
            ?: formats.firstOrNull { it == TextureFormat.BGRA8UnormSrgb }
            ?: formats.firstOrNull()
            ?: TextureFormat.BGRA8Unorm
    }

    /** Renders and presents one frame; runs on the WebGPU dispatcher. */
    private fun drawFrame(environment: WebGpu) {
        try {
            drawFrameInternal(environment)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "WebGPU frame failed" }
        }
    }

    private fun drawFrameInternal(environment: WebGpu) {
        val pipe = pipeline ?: return
        val params = paramsBuffer ?: return
        val pageSampler = sampler ?: return
        val groupLayout = bindGroupLayout ?: return
        val page = currentPage?.takeIf { !it.destroyed }

        val frame: GPUSurfaceTexture = try {
            environment.webgpuSurface.getCurrentTexture()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "getCurrentTexture failed" }
            return
        }
        val acquired = frame.status == SurfaceGetCurrentTextureStatus.SuccessOptimal ||
            frame.status == SurfaceGetCurrentTextureStatus.SuccessSuboptimal
        if (!acquired) return

        // The backbuffer texture is owned by the surface: never close it, only the views below.
        val backbuffer = frame.texture.createView()
        val pageView = page?.texture?.createView()
        val bindGroup: GPUBindGroup? = pageView?.let { view ->
            environment.device.createBindGroup(
                GPUBindGroupDescriptor(
                    layout = groupLayout,
                    entries = arrayOf(
                        GPUBindGroupEntry(binding = 0, buffer = params),
                        GPUBindGroupEntry(binding = 1, sampler = pageSampler),
                        GPUBindGroupEntry(binding = 2, textureView = view),
                    ),
                ),
            )
        }
        try {
            writeParams(environment.device, params, page)
            val encoder: GPUCommandEncoder = environment.device.createCommandEncoder()
            var commands: GPUCommandBuffer? = null
            try {
                val pass: GPURenderPassEncoder = encoder.beginRenderPass(
                    GPURenderPassDescriptor(
                        colorAttachments = arrayOf(
                            GPURenderPassColorAttachment(
                                clearValue = GPUColor(0.0, 0.0, 0.0, 1.0),
                                view = backbuffer,
                                loadOp = LoadOp.Clear,
                                storeOp = StoreOp.Store,
                            ),
                        ),
                    ),
                )
                try {
                    // bindGroup is only non-null when page is, so it gates both.
                    if (bindGroup != null) {
                        pass.setPipeline(pipe)
                        pass.setBindGroup(0, bindGroup)
                        pass.draw(6)
                    }
                    pass.end()
                } finally {
                    pass.close()
                }
                commands = encoder.finish()
                environment.device.queue.submit(arrayOf(commands))
            } finally {
                commands?.close()
                encoder.close()
            }
            environment.webgpuSurface.present()
        } finally {
            bindGroup?.close()
            pageView?.close()
            backbuffer.close()
        }
    }

    /** Writes the aspect-fit quad transform (NDC half-extents, centered) for [page]. */
    private fun writeParams(device: GPUDevice, params: GPUBuffer, page: PageImage?) {
        var scaleX = 0f
        var scaleY = 0f
        if (page != null) {
            val fit = min(surfaceWidth.toFloat() / page.width, surfaceHeight.toFloat() / page.height)
            scaleX = page.width * fit / surfaceWidth
            scaleY = page.height * fit / surfaceHeight
        }
        val data = ByteBuffer.allocate(PARAMS_BUFFER_SIZE.toInt()).order(ByteOrder.nativeOrder())
        data.putFloat(scaleX)
        data.putFloat(scaleY)
        data.putFloat(0f)
        data.putFloat(0f)
        data.rewind()
        device.queue.writeBuffer(params, 0, data)
    }

    override fun isDestroyed(image: PageImage): Boolean = image.destroyed

    override fun destroy(image: PageImage) {
        if (image.destroyed) return
        image.destroyed = true
        val environment = synchronized(lock) {
            // Absent from liveImages: either already released or owned by a torn-down environment.
            if (!liveImages.remove(image)) return
            webgpu
        } ?: return
        runBlocking {
            environment.execute {
                runCatching { image.texture.close() }
            }
        }
    }

    override fun invalidate(image: PageImage) {
        if (!image.destroyed) requestRender()
    }

    override suspend fun createFromBitmap(bitmap: Bitmap): PageImage {
        val pixels = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val buffer = ByteBuffer.allocateDirect(pixels.byteCount).order(ByteOrder.nativeOrder())
        pixels.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return createFromImage(buffer, pixels.width, pixels.height)
    }

    override suspend fun createFromImage(image: ByteBuffer, width: Int, height: Int): PageImage {
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid frame size ${width}x$height")
        }
        val environment = synchronized(lock) { webgpu }
            ?: throw IllegalStateException("WebGPU renderer is not running")
        return environment.execute {
            if (stopped) throw IllegalStateException("WebGPU renderer stopped during upload")
            val device = environment.device
            val texture = device.createTexture(
                GPUTextureDescriptor(
                    usage = TextureUsage.TextureBinding or TextureUsage.CopyDst,
                    size = GPUExtent3D(width = width, height = height),
                    format = TextureFormat.RGBA8Unorm,
                ),
            )
            uploadPixels(device, texture, image, width, height)
            val page = PageImage(texture, width, height)
            val accepted = synchronized(lock) {
                if (stopped) {
                    false
                } else {
                    liveImages.add(page)
                    true
                }
            }
            if (!accepted) {
                texture.close()
                throw IllegalStateException("WebGPU renderer stopped during upload")
            }
            page
        }
    }

    /**
     * Uploads tightly packed RGBA8 [pixels] into [texture]. Dawn enforces WebGPU's 256-byte copy
     * stride, so rows are re-packed into a padded staging buffer when the natural stride misses it.
     */
    private fun uploadPixels(
        device: GPUDevice,
        texture: GPUTexture,
        pixels: ByteBuffer,
        width: Int,
        height: Int,
    ) {
        val rowBytes = width * BYTES_PER_PIXEL
        val writeSize = GPUExtent3D(width = width, height = height)
        val source = pixels.duplicate().order(ByteOrder.nativeOrder())
        if (source.remaining() < rowBytes * height) {
            // Some decoder buffers arrive positioned at 0 without remaining() set.
            source.rewind()
        }
        if (rowBytes % TEXTURE_COPY_ALIGNMENT == 0) {
            device.queue.writeTexture(
                destination = GPUTexelCopyTextureInfo(texture = texture),
                data = source,
                writeSize = writeSize,
                dataLayout = GPUTexelCopyBufferLayout(bytesPerRow = rowBytes, rowsPerImage = height),
            )
            return
        }
        val paddedRowBytes = (rowBytes + TEXTURE_COPY_ALIGNMENT - 1) / TEXTURE_COPY_ALIGNMENT *
            TEXTURE_COPY_ALIGNMENT
        val staging = ByteBuffer.allocateDirect(paddedRowBytes * height).order(ByteOrder.nativeOrder())
        repeat(height) {
            source.limit(source.position() + rowBytes)
            staging.put(source)
            source.limit(source.capacity())
        }
        staging.flip()
        device.queue.writeTexture(
            destination = GPUTexelCopyTextureInfo(texture = texture),
            data = staging,
            writeSize = writeSize,
            dataLayout = GPUTexelCopyBufferLayout(bytesPerRow = paddedRowBytes, rowsPerImage = height),
        )
    }
}
