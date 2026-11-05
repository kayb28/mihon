package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.graphics.Bitmap
import android.view.Surface
import androidx.webgpu.BufferUsage
import androidx.webgpu.DeviceLostCallback
import androidx.webgpu.DeviceLostException
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
import androidx.webgpu.GPUDeviceDescriptor
import androidx.webgpu.GPUExtent3D
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPULimits
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
import androidx.webgpu.GPUTextureView
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.StoreOp
import androidx.webgpu.SurfaceGetCurrentTextureStatus
import androidx.webgpu.TextureFormat
import androidx.webgpu.TextureUsage
import androidx.webgpu.UncapturedErrorCallback
import androidx.webgpu.WebGpuRuntimeException
import androidx.webgpu.helper.WebGpu
import androidx.webgpu.helper.createWebGpu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** vec2f scale + vec2f offset + vec4f color ops, all NDC/flag units; size stays 16-byte aligned. */
private const val PARAMS_STRUCT_SIZE = 32L

/** Uniform buffer slots are 256 bytes apart: uniform bind group offsets align to 256. */
private const val PARAMS_SLOT = 256

/** Pages drawable in one frame: the continuous viewer never shows more than this at once. */
private const val PARAMS_SLOTS = 12

private const val BYTES_PER_PIXEL = 4

/** Dawn enforces the WebGPU spec's 256-byte copy stride for texture uploads. */
private const val TEXTURE_COPY_ALIGNMENT = 256

/** Whole uniform buffer: one 256-byte slot per drawable page, each holding the 16-byte struct. */
private val PARAMS_BUFFER_SIZE = (PARAMS_SLOT * PARAMS_SLOTS).toLong()

/** First scratch allocation; page uploads grow it to their padded-row size and keep it. */
private const val STAGING_INITIAL_CAPACITY = 64 * 1024

/**
 * WebGPU's default `maxTextureDimension2D` (8192) is too low for the tall pages webtoon sources
 * ship — single-page strips of 12000–16000 px are routine. With the helper's default device
 * descriptor (no `requiredLimits`), Dawn caps the dimension at 8192 and `device.createTexture`
 * throws `ValidationException` for any page taller than that; the reader eats the exception per
 * page and shows a black screen (POCO peridot, Android 16, build 8b00e5b: "hace la animación de
 * carga del capítulo pero no muestra imagen solo negro").
 *
 * 16384 is the WebGPU "default + 1" tier — every adapter that supports the 8192 default also
 * supports 16384 per the spec, and the device's own error message names this exact value as the
 * adapter's actual maximum. Pages taller than 16384 would need tile-split rendering, tracked
 * separately.
 */
private const val MAX_TEXTURE_DIMENSION_2D = 16384

/**
 * The executor the device's error callbacks run on. Matches the androidx.webgpu.helper default:
 * run inline on the WebGPU dispatcher thread (the helper builds its own descriptor with the same
 * `Executor { it.run() }` — see `WebGpuUtils.getDefaultDeviceDescriptor`). We replicate it here
 * because [GPUDeviceDescriptor]'s constructor makes the executor and callback params required.
 */
private val DIRECT_EXECUTOR = Executor { it.run() }

/**
 * Builds the [GPUDeviceDescriptor] the renderer hands to `createWebGpu`: asks for
 * `maxTextureDimension2D = [MAX_TEXTURE_DIMENSION_2D]` and lets every other limit fall back to
 * the adapter's spec defaults (a 0 in `requiredLimits` means "use default"). The error callbacks
 * mirror the helper's defaults — surface device-lost and uncaptured errors as exceptions so the
 * reader's `catch (e: Exception)` path turns them into the per-page error overlay instead of
 * silently swallowing them.
 *
 * `internal` so the JVM regression test in `AndroidxWebGpuDeviceDescriptorTest` can assert the
 * field without spinning up the renderer (which needs a real GPU).
 */
internal fun buildWebGpuDeviceDescriptor(): GPUDeviceDescriptor = GPUDeviceDescriptor(
    deviceLostCallbackExecutor = DIRECT_EXECUTOR,
    uncapturedErrorCallbackExecutor = DIRECT_EXECUTOR,
    requiredLimits = GPULimits(maxTextureDimension2D = MAX_TEXTURE_DIMENSION_2D),
    deviceLostCallback = object : DeviceLostCallback {
        override fun onDeviceLost(device: GPUDevice, reason: Int, message: String) {
            throw DeviceLostException(device, reason, message)
        }
    },
    uncapturedErrorCallback = object : UncapturedErrorCallback {
        override fun onUncapturedError(device: GPUDevice, type: Int, message: String) {
            throw WebGpuRuntimeException.create(type, message)
        }
    },
)

/** Fullscreen-quad pipeline: scales the page texture with the uniform's NDC transform. */
private val VIEWER_WGSL = """
    struct Params {
        scale: vec2f,
        offset: vec2f,
        // x: grayscale amount, y: invert amount — the stock reader's grayscale/inverted-colors
        // settings, applied here because a SurfaceView cannot take a Compose ColorFilter.
        colorOp: vec4f,
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
        var color = textureSample(pageTexture, pageSampler, in.uv);
        // Same order the stock ColorMatrix composes them: grayscale first (saturation 0, the
        // same luminance weights ColorMatrix.setToSaturation uses), then color inversion.
        let gray = dot(color.rgb, vec3f(0.213, 0.715, 0.072));
        color = vec4f(mix(color.rgb, vec3f(gray), params.colorOp.x), color.a);
        color = vec4f(mix(color.rgb, vec3f(1.0) - color.rgb, params.colorOp.y), color.a);
        return color;
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
 * Frames are paced by [frameLoop]: at most one draw per display frame however many
 * [requestRender] calls arrived, and none at all while nothing is dirty.
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

    /**
     * One page quad the next frame draws, placed by the viewer: NDC half-extents and center
     * offset. The continuous viewer places every visible page this way; the paged viewer keeps
     * the single-page [setPage] path, which computes the same thing from its transform.
     */
    class PageDraw internal constructor(
        internal val image: PageImage,
        internal val scaleX: Float,
        internal val scaleY: Float,
        internal val offsetX: Float,
        internal val offsetY: Float,
    )

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

    /** Latest configured surface size, readable from any thread (viewers lay pages out with it). */
    @Volatile
    var surfaceWidthPx: Int = 1
        private set

    @Volatile
    var surfaceHeightPx: Int = 1
        private set

    // Only touched from the WebGPU dispatcher thread.
    private var surfaceFormat: Int = TextureFormat.RGBA8Unorm
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    /** Images alive on the current environment; all released by [stop] (they die with the device). */
    private val liveImages = LinkedHashSet<PageImage>()

    private var renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

    /**
     * Anything changed since the last frame, however many requestRender calls said so - the
     * coalescing flag of the frame loop below.
     */
    private val renderDirty = AtomicBoolean(false)

    /** Wakes the frame loop when there is nothing to draw; buffered, so no send races the dirty check. */
    private val renderWake = Channel<Unit>(Channel.CONFLATED)

    /** The frame loop's scope: Main dispatcher + the Choreographer frame clock. */
    private var pacingScope: CoroutineScope? = null

    /** The running frame loop, if any; cancelled by [stop] and restarted by [start]. */
    private var frameLoopJob: Job? = null

    /**
     * The draw the loop launched, if one is still in flight; while it runs the loop holds the
     * dirty flag for the next frame instead of piling stale renders onto the dispatcher.
     */
    @Volatile
    private var drawingJob: Job? = null

    /** The page the next frame draws; null clears the surface. */
    @Volatile
    private var currentPage: PageImage? = null

    /** The pages the next frame draws when the continuous viewer has placed some; wins over [currentPage]. */
    @Volatile
    private var placedPages: List<PageDraw> = emptyList()

    /**
     * CPU scratch reused across uploads and frames: texture rows re-packed to the 256-byte copy
     * stride (up to a page's padded size) and the per-frame uniform data. Only ever touched on
     * the WebGPU dispatcher thread, inside [WebGpu.execute], so one grow-only instance needs no
     * lock; without it every page upload allocated and dropped a direct buffer the size of the
     * image, and every frame allocated its uniforms.
     */
    private var scratchBuffer: ByteBuffer? = null

    /** Page zoom applied to the drawn page; 1 shows the whole page aspect-fitted. */
    @Volatile
    private var pageZoom = 1f

    /** Page pan in surface pixels (y down); clamped per frame so a zoomed page fills the view. */
    @Volatile
    private var pagePanX = 0f

    @Volatile
    private var pagePanY = 0f

    /** Grayscale amount (0-1) the fragment shader mixes toward luminance. */
    @Volatile
    private var colorGrayscale = 0f

    /** Invert amount (0-1) the fragment shader flips colors by. */
    @Volatile
    private var colorInvert = 0f

    /**
     * The colour the surface clears to wherever a page does not cover - gaps between pages,
     * side padding, a page still uploading. The viewers push the reader's theme colour here;
     * the black this used to hardcode read as black bars between pages, where every stock
     * viewer shows the reader background. Opaque ARGB.
     */
    @Volatile
    private var backgroundColor = 0xFF000000.toInt()

    /** Sets the clear colour for the next frame and redraws; see [backgroundColor]. */
    fun setBackgroundColor(argb: Int) {
        if (backgroundColor == argb) return
        backgroundColor = argb
        requestRender()
    }

    val isRunning: Boolean
        get() = synchronized(lock) { webgpu != null }

    /** True while [environment] is still the live one; stale jobs of a torn-down one no-op. */
    private fun isCurrent(environment: WebGpu): Boolean = synchronized(lock) { webgpu === environment }

    /** Sets the page drawn by the next frame (`null` clears the surface). */
    fun setPage(image: PageImage?) {
        currentPage = image
    }

    /**
     * Sets the zoom and pan the next frame draws [currentPage] with. [panX]/[panY] are surface
     * pixels (y down, Compose coordinates as-is); the frame clamps them so a zoomed page keeps
     * filling the viewport and a page fitting it cannot be panned at all.
     */
    fun setTransform(zoom: Float, panX: Float, panY: Float) {
        pageZoom = zoom
        pagePanX = panX
        pagePanY = panY
    }

    /**
     * Sets the pages the next frame draws, each already placed in NDC by its viewer; drawn in
     * order, page quads never overlap. An empty list falls back to the single-page [setPage] path.
     */
    fun setPages(pages: List<PageDraw>) {
        placedPages = pages
    }

    /**
     * Sets the color transform every drawn page gets: [grayscale] and [inverted] mix amounts in
     * [0, 1] (the stock reader passes booleans, so 0/1 from the viewers). Applied per fragment
     * in the shader — a SurfaceView cannot take a Compose [android.graphics.ColorFilter], which
     * is why the androidx viewers lost the reader's grayscale/inverted settings until now.
     */
    fun setColorFilter(grayscale: Float, inverted: Float) {
        colorGrayscale = grayscale.coerceIn(0f, 1f)
        colorInvert = inverted.coerceIn(0f, 1f)
    }

    /**
     * Brings up the GPU environment bound to [surface], configured for [width] x [height], and
     * creates the pipeline the frames use. Replaces any environment still running.
     */
    suspend fun start(surface: Surface, width: Int, height: Int) {
        if (isRunning) stop()
        // Pass an explicit device descriptor (not the helper's no-requiredLimits default) so the
        // device's maxTextureDimension2D is bumped to 16384 — see buildWebGpuDeviceDescriptor().
        val environment = try {
            createWebGpu(surface = surface, deviceDescriptor = buildWebGpuDeviceDescriptor())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Some adapters only expose the WebGPU spec default for maxTextureDimension2D (8192)
            // and reject a device request for more. Losing WebGPU entirely over a page-size
            // limit is worse than the lower ceiling: retry on the helper's default descriptor
            // and let pages taller than 8192 fail per page (their error overlay says why). Any
            // other bring-up failure fails again here and surfaces to the viewer's fallback.
            logcat(LogPriority.WARN, e) {
                "device request with maxTextureDimension2D=16384 failed; retrying with adapter defaults"
            }
            createWebGpu(surface = surface)
        }
        try {
            environment.execute {
                surfaceFormat = pickSurfaceFormat(environment.webgpuSurface.getCapabilities(environment.adapter))
                surfaceWidth = width.coerceAtLeast(1)
                surfaceHeight = height.coerceAtLeast(1)
                surfaceWidthPx = surfaceWidth
                surfaceHeightPx = surfaceHeight
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
            startFrameLoop()
        }
    }

    /** Starts the paced frame loop on [pacingScope]; callers hold [lock]. */
    private fun startFrameLoop() {
        val scope = pacingScope?.takeIf { it.isActive }
            ?: CoroutineScope(SupervisorJob() + Dispatchers.Main + choreographerFrameClock)
                .also { pacingScope = it }
        frameLoopJob?.cancel()
        frameLoopJob = scope.launch { frameLoop() }
    }

    /** Reconfigures the surface for a new size and redraws the current page. */
    fun resize(width: Int, height: Int) {
        val environment = synchronized(lock) { webgpu } ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        renderScope.launch {
            renderMutex.withLock {
                environment.execute {
                    if (stopped || !isCurrent(environment)) return@execute
                    surfaceWidth = w
                    surfaceHeight = h
                    surfaceWidthPx = w
                    surfaceHeightPx = h
                    configureSurface(environment, w, h)
                    drawFrame(environment)
                }
            }
        }
    }

    /**
     * Schedules a frame of the current page state (or a clear when none) onto the surface:
     * marks the frame loop's dirty flag and wakes it. Nothing renders inline here - every
     * request between frames coalesces into the loop's single draw, so a scroll tick, a
     * gesture step and a page upload all land in one frame instead of one GPU render each.
     */
    fun requestRender() {
        if (synchronized(lock) { webgpu } == null) return
        renderDirty.set(true)
        renderWake.trySend(Unit)
    }

    /**
     * Draws at most one frame per display frame, for as long as anything is dirty.
     *
     * This is the frame discipline ca.mpreg's `ImageViewerState.collect()` runs with, and the
     * one the stock viewers inherit from the Android view system: invalidations only mark
     * [renderDirty], the loop draws at most one frame per vsync however many requests arrived,
     * parks on [renderWake] while clean (no Choreographer ticks, no GPU work, nothing to heat
     * the phone while idle), and a draw still in flight holds the dirty flag for the next
     * frame. The un-paced requestRender of earlier builds rendered once per call - per scroll
     * tick, per gesture step - and those renders queued behind the render mutex kept draining
     * stale frames long after the interaction stopped.
     */
    private suspend fun frameLoop() = coroutineScope {
        while (true) {
            choreographerFrameClock.withFrameNanos { }
            if (drawingJob?.isActive == true) continue
            if (!renderDirty.getAndSet(false)) {
                renderWake.receive()
                continue
            }
            // Drop the wake this frame's request left, or going idle costs a spurious frame.
            renderWake.tryReceive()
            val environment = synchronized(lock) { webgpu } ?: break
            drawingJob = renderScope.launch {
                renderMutex.withLock {
                    environment.execute {
                        if (stopped || !isCurrent(environment)) return@execute
                        drawFrame(environment)
                    }
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
            frameLoopJob = null
            drawingJob = null
            pacingScope?.cancel()
            pacingScope = null
        }
        runBlocking {
            environment.execute {
                toRelease.forEach { image -> runCatching { image.texture.close() } }
                releaseResources()
            }
        }
        // The scratch lived on the dispatcher thread that just drained; a parked page-sized
        // buffer is not worth keeping across environments.
        scratchBuffer = null
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
        try {
            val placed = placedPages.filter { !it.image.destroyed }.take(PARAMS_SLOTS)
            if (placed.isNotEmpty()) {
                drawPlacedPages(environment, pipe, params, pageSampler, groupLayout, backbuffer, placed)
            } else {
                drawSinglePage(environment, pipe, params, pageSampler, groupLayout, backbuffer)
            }
        } finally {
            backbuffer.close()
        }
    }

    /** Draws the continuous viewer's placed pages; one uniform slot and bind group per page. */
    private fun drawPlacedPages(
        environment: WebGpu,
        pipe: GPURenderPipeline,
        params: GPUBuffer,
        pageSampler: GPUSampler,
        groupLayout: GPUBindGroupLayout,
        backbuffer: GPUTextureView,
        placed: List<PageDraw>,
    ) {
        writePlacedParams(environment.device, params, placed)
        val encoder: GPUCommandEncoder = environment.device.createCommandEncoder()
        var commands: GPUCommandBuffer? = null
        val views = ArrayList<GPUTextureView>(placed.size)
        val groups = ArrayList<GPUBindGroup>(placed.size)
        try {
            val pass: GPURenderPassEncoder = encoder.beginRenderPass(
                GPURenderPassDescriptor(
                    colorAttachments = arrayOf(
                        GPURenderPassColorAttachment(
                            clearValue = backgroundClearValue(),
                            view = backbuffer,
                            loadOp = LoadOp.Clear,
                            storeOp = StoreOp.Store,
                        ),
                    ),
                ),
            )
            try {
                placed.forEachIndexed { index, draw ->
                    val view = draw.image.texture.createView()
                    views.add(view)
                    val group = environment.device.createBindGroup(
                        GPUBindGroupDescriptor(
                            layout = groupLayout,
                            entries = arrayOf(
                                GPUBindGroupEntry(
                                    binding = 0,
                                    buffer = params,
                                    offset = (index * PARAMS_SLOT).toLong(),
                                    size = PARAMS_SLOT.toLong(),
                                ),
                                GPUBindGroupEntry(binding = 1, sampler = pageSampler),
                                GPUBindGroupEntry(binding = 2, textureView = view),
                            ),
                        ),
                    )
                    groups.add(group)
                    pass.setPipeline(pipe)
                    pass.setBindGroup(0, group)
                    pass.draw(6)
                }
                pass.end()
            } finally {
                pass.close()
            }
            commands = encoder.finish()
            environment.device.queue.submit(arrayOf(commands))
            environment.webgpuSurface.present()
        } finally {
            groups.forEach { runCatching { it.close() } }
            views.forEach { runCatching { it.close() } }
            commands?.close()
            encoder.close()
        }
    }

    /** Writes one 256-byte uniform slot per placed page (the struct lives at the slot's start). */
    private fun writePlacedParams(device: GPUDevice, params: GPUBuffer, placed: List<PageDraw>) {
        val data = scratch(placed.size * PARAMS_SLOT)
        placed.forEach { draw ->
            data.putFloat(draw.scaleX)
            data.putFloat(draw.scaleY)
            data.putFloat(draw.offsetX)
            data.putFloat(draw.offsetY)
            data.putFloat(colorGrayscale)
            data.putFloat(colorInvert)
            data.putFloat(0f)
            data.putFloat(0f)
            data.position(data.position() + PARAMS_SLOT - PARAMS_STRUCT_SIZE.toInt())
        }
        data.flip()
        // The JNI sizes queue writes by capacity, not remaining: confine first or Dawn rejects
        // the write once the grow-only scratch has outgrown this 32-byte payload.
        device.queue.writeBuffer(params, 0, AndroidxWebGpuBufferOps.confine(data))
    }

    /** Draws the paged viewer's single [currentPage] with its zoom/pan transform. */
    private fun drawSinglePage(
        environment: WebGpu,
        pipe: GPURenderPipeline,
        params: GPUBuffer,
        pageSampler: GPUSampler,
        groupLayout: GPUBindGroupLayout,
        backbuffer: GPUTextureView,
    ) {
        val page = currentPage?.takeIf { !it.destroyed }
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
                                clearValue = backgroundClearValue(),
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
        }
    }

    /** Writes the aspect-fit quad transform (NDC half-extents, centered) for [page]. */
    private fun writeParams(device: GPUDevice, params: GPUBuffer, page: PageImage?) {
        var scaleX = 0f
        var scaleY = 0f
        var offsetX = 0f
        var offsetY = 0f
        if (page != null) {
            val zoom = pageZoom.coerceAtLeast(1f)
            val fit = min(surfaceWidth.toFloat() / page.width, surfaceHeight.toFloat() / page.height)
            // Zoom about the page center, then pan: NDC scale grows, the pan follows in NDC units
            // (surface px are y down, NDC y up, so the pan's y flips).
            scaleX = page.width * fit / surfaceWidth * zoom
            scaleY = page.height * fit / surfaceHeight * zoom
            val maxPanX = (scaleX - 1f).coerceAtLeast(0f)
            val maxPanY = (scaleY - 1f).coerceAtLeast(0f)
            offsetX = (pagePanX / surfaceWidth * 2f).coerceIn(-maxPanX, maxPanX)
            offsetY = (-pagePanY / surfaceHeight * 2f).coerceIn(-maxPanY, maxPanY)
        }
        val data = scratch(PARAMS_STRUCT_SIZE.toInt())
        data.putFloat(scaleX)
        data.putFloat(scaleY)
        data.putFloat(offsetX)
        data.putFloat(offsetY)
        data.putFloat(colorGrayscale)
        data.putFloat(colorInvert)
        data.putFloat(0f)
        data.putFloat(0f)
        data.flip()
        device.queue.writeBuffer(params, 0, AndroidxWebGpuBufferOps.confine(data))
    }

    /** The clear colour every render pass starts from: the reader's background, opaque. */
    private fun backgroundClearValue(): GPUColor {
        val argb = backgroundColor
        return GPUColor(
            ((argb shr 16) and 0xFF) / 255.0,
            ((argb shr 8) and 0xFF) / 255.0,
            (argb and 0xFF) / 255.0,
            1.0,
        )
    }

    /** Returns the dispatcher-confined scratch buffer, cleared, holding at least [capacity]. */
    private fun scratch(capacity: Int): ByteBuffer {
        val current = scratchBuffer
        if (current != null && current.capacity() >= capacity) {
            current.clear()
            return current
        }
        val grown = ByteBuffer.allocateDirect(maxOf(capacity, STAGING_INITIAL_CAPACITY))
            .order(ByteOrder.nativeOrder())
        scratchBuffer = grown
        return grown
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
        require(source.remaining() >= rowBytes * height) {
            "Pixel buffer holds ${source.remaining()} bytes, need ${rowBytes * height} for ${width}x$height"
        }
        // The buffer's meaningful end: limit() is restored to it after every row below —
        // capacity is not the data end for every decoder buffer.
        val sourceEnd = source.limit()
        if (rowBytes % TEXTURE_COPY_ALIGNMENT == 0) {
            device.queue.writeTexture(
                destination = GPUTexelCopyTextureInfo(texture = texture),
                data = AndroidxWebGpuBufferOps.confine(source),
                writeSize = writeSize,
                dataLayout = GPUTexelCopyBufferLayout(bytesPerRow = rowBytes, rowsPerImage = height),
            )
            return
        }
        val paddedRowBytes = (rowBytes + TEXTURE_COPY_ALIGNMENT - 1) / TEXTURE_COPY_ALIGNMENT *
            TEXTURE_COPY_ALIGNMENT
        val staging = scratch(paddedRowBytes * height)
        repeat(height) {
            source.limit(source.position() + rowBytes)
            staging.put(source)
            source.limit(sourceEnd)
            // Rows must land on the padded stride: put() advances by rowBytes only, so skip to
            // the next row's aligned start (the same slot-stride trick the uniform writes use).
            staging.position(staging.position() + paddedRowBytes - rowBytes)
        }
        staging.flip()
        device.queue.writeTexture(
            destination = GPUTexelCopyTextureInfo(texture = texture),
            data = AndroidxWebGpuBufferOps.confine(staging),
            writeSize = writeSize,
            dataLayout = GPUTexelCopyBufferLayout(bytesPerRow = paddedRowBytes, rowsPerImage = height),
        )
    }
}
