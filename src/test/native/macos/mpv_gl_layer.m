// Spike code (B-13, embed spike 2026-11-14). Not production code.
//
// A CAOpenGLLayer that renders libmpv with the render API (vo=libmpv, OpenGL). The layer goes into a heavyweight
// AWT Canvas through the JAWT surface layers of macOS. The JDK keeps the frame of the layer equal to the bounds of
// the canvas. The design follows the cocoa-cb layer of mpv: the mpv update callback calls -display on a serial queue.
//
// Environment variables:
//   MPV_LAYER_ASYNC=0      the cocoa-cb method (default is asynchronous = YES; the spike recommends 0)
//   MPV_LAYER_OFFSCREEN=1  an own 60 Hz timer renders into an own framebuffer, with no Core Animation
//   MPV_LAYER_READBACK=1   reads each frame back to memory, to measure a Swing image path
//
// Build (see MpvRenderApiSpikeMain.kt):
//   clang -dynamiclib -fobjc-arc -O2 -o target/libmpvgllayer.dylib src/test/native/macos/mpv_gl_layer.m \
//     -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -I/opt/homebrew/include \
//     -L"$JAVA_HOME/lib" -ljawt -Wl,-rpath,"$JAVA_HOME/lib" -L/opt/homebrew/lib -lmpv \
//     -framework Cocoa -framework QuartzCore -framework OpenGL -framework ImageIO -framework UniformTypeIdentifiers
#define GL_SILENCE_DEPRECATION 1
#import <Cocoa/Cocoa.h>
#import <QuartzCore/QuartzCore.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#import <ImageIO/ImageIO.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <mpv/client.h>
#include <mpv/render_gl.h>
#include <dlfcn.h>
#include <stdatomic.h>
#include <time.h>

static uint64_t now_ns(void) { return clock_gettime_nsec_np(CLOCK_UPTIME_RAW); }

static void *get_proc_address(void *ctx, const char *name) {
    static void *lib;
    if (!lib) lib = dlopen("/System/Library/Frameworks/OpenGL.framework/OpenGL", RTLD_LAZY);
    return dlsym(lib, name);
}

@interface MpvGlLayer : CAOpenGLLayer {
@public
    mpv_render_context *render;
    CGLContextObj cgl;
    CGLPixelFormatObj pix;
    dispatch_queue_t queue;
    atomic_bool force;
    // Statistics.
    atomic_llong renders, newFrames, renderNsSum, renderNsMax, lateIntervals, lastRenderNs, intervalSum, intervalCount;
    // The probe pixel (fractions of the layer), read after each render when probeOn.
    atomic_bool probeOn;
    double probeX, probeY;
    atomic_llong probeNs, probePixel;
    NSString *capturePath;
    atomic_bool captureDone;
    dispatch_source_t offscreenTimer;
}
@end

@implementation MpvGlLayer

- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask { return CGLRetainPixelFormat(pix); }
- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)pf { return CGLRetainContext(cgl); }

static atomic_llong dbgUpdates, dbgDisplay, dbgCanDraw, dbgCanDrawYes, dbgDraw, dbgViewport;

static BOOL offscreenMode(void) { const char *v = getenv("MPV_LAYER_OFFSCREEN"); return v && strcmp(v, "1") == 0; }
static BOOL asyncMode(void) { const char *v = getenv("MPV_LAYER_ASYNC"); return !v || strcmp(v, "0") != 0; }

- (void)update:(BOOL)forced {
    if (forced) atomic_store(&force, true);
    atomic_fetch_add(&dbgUpdates, 1);
    // Asynchronous mode: Core Animation calls canDraw at each display refresh, so an update needs no call here.
    if (offscreenMode() || (asyncMode() && !forced)) return;
    dispatch_async(queue, ^{ atomic_fetch_add(&dbgDisplay, 1); [self setNeedsDisplay]; [self display]; [CATransaction flush]; });
}

- (BOOL)canDrawInCGLContext:(CGLContextObj)ctx pixelFormat:(CGLPixelFormatObj)pf
               forLayerTime:(CFTimeInterval)t displayTime:(const CVTimeStamp *)ts {
    if (!render) return NO;
    atomic_fetch_add(&dbgCanDraw, 1);
    uint64_t flags = mpv_render_context_update(render);
    BOOL yes = (flags & MPV_RENDER_UPDATE_FRAME) || atomic_load(&force);
    if (yes) atomic_fetch_add(&dbgCanDrawYes, 1);
    return yes;
}

- (void)drawInCGLContext:(CGLContextObj)ctx pixelFormat:(CGLPixelFormatObj)pf
            forLayerTime:(CFTimeInterval)t displayTime:(const CVTimeStamp *)ts {
    atomic_store(&force, false);
    GLint fbo = 0, dims[4] = {0};
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &fbo);
    glGetIntegerv(GL_VIEWPORT, dims);
    int w = dims[2], h = dims[3];
    if (w <= 0 || h <= 0) {
        w = (int)(self.bounds.size.width * self.contentsScale);
        h = (int)(self.bounds.size.height * self.contentsScale);
    }
    dbgViewport = ((long long)dims[2] << 32) | dims[3];
    atomic_fetch_add(&dbgDraw, 1);
    if (w <= 0 || h <= 0) return;
    [self renderFbo:fbo width:w height:h];
}

- (void)renderFbo:(GLint)fbo width:(int)w height:(int)h {
    mpv_opengl_fbo target = { .fbo = fbo, .w = w, .h = h, .internal_format = 0 };
    int flip = 1;
    int block = 0;
    mpv_render_param params[] = {
        {MPV_RENDER_PARAM_OPENGL_FBO, &target},
        {MPV_RENDER_PARAM_FLIP_Y, &flip},
        {MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME, &block},
        {0},
    };
    uint64_t start = now_ns();
    mpv_render_context_render(render, params);
    glFlush();
    mpv_render_context_report_swap(render);
    if (getenv("MPV_LAYER_READBACK")) {
        // Readback test: copy the full frame to memory, as a Swing image path would need.
        static uint8_t *buf; static size_t bufSize;
        size_t need = (size_t)w * h * 4;
        if (bufSize < need) { free(buf); buf = malloc(need); bufSize = need; }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
    }
    uint64_t end = now_ns();
    long long dur = (long long)(end - start);
    atomic_fetch_add(&renders, 1);
    atomic_fetch_add(&renderNsSum, dur);
    long long max = atomic_load(&renderNsMax);
    if (dur > max) atomic_store(&renderNsMax, dur);
    long long last = atomic_exchange(&lastRenderNs, (long long)end);
    if (last > 0) {
        long long interval = (long long)end - last;
        atomic_fetch_add(&intervalSum, interval);
        atomic_fetch_add(&intervalCount, 1);
        if (interval > 25000000LL) atomic_fetch_add(&lateIntervals, 1);
    }
    if (atomic_load(&probeOn)) {
        GLubyte px[4] = {0};
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glReadPixels((GLint)(probeX * w), (GLint)((1.0 - probeY) * h), 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
        atomic_store(&probePixel, (long long)((px[0] << 16) | (px[1] << 8) | px[2]));
        atomic_store(&probeNs, (long long)now_ns());
    }
    NSString *path = capturePath;
    if (path) {
        capturePath = nil;
        [self writePng:path fbo:fbo width:w height:h];
        atomic_store(&captureDone, true);
    }
}

- (void)writePng:(NSString *)path fbo:(GLint)fbo width:(int)w height:(int)h {
    size_t stride = (size_t)w * 4;
    uint8_t *data = malloc(stride * h);
    uint8_t *flipped = malloc(stride * h);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data);
    for (int y = 0; y < h; y++) memcpy(flipped + (size_t)y * stride, data + (size_t)(h - 1 - y) * stride, stride);
    CGColorSpaceRef cs = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef bc = CGBitmapContextCreate(flipped, w, h, 8, stride, cs, kCGImageAlphaNoneSkipLast);
    CGImageRef image = CGBitmapContextCreateImage(bc);
    CGImageDestinationRef dest = CGImageDestinationCreateWithURL(
        (__bridge CFURLRef)[NSURL fileURLWithPath:path], (__bridge CFStringRef)UTTypePNG.identifier, 1, NULL);
    CGImageDestinationAddImage(dest, image, NULL);
    CGImageDestinationFinalize(dest);
    CFRelease(dest);
    CGImageRelease(image);
    CGContextRelease(bc);
    CGColorSpaceRelease(cs);
    free(data);
    free(flipped);
}

// Offscreen mode: a 60 Hz timer renders into an own framebuffer of the layer size. No Core Animation.
- (void)startOffscreen {
    __block GLuint fbo = 0, tex = 0;
    __block int fw = 0, fh = 0;
    dispatch_source_t timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, queue);
    dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, 0), 16666667ULL, 0);
    dispatch_source_set_event_handler(timer, ^{
        if (!self->render) return;
        int w = (int)(self.bounds.size.width * self.contentsScale);
        int h = (int)(self.bounds.size.height * self.contentsScale);
        if (w <= 0 || h <= 0) return;
        CGLSetCurrentContext(self->cgl);
        if (w != fw || h != fh) {
            if (fbo) { glDeleteFramebuffers(1, &fbo); glDeleteTextures(1, &tex); }
            glGenTextures(1, &tex);
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
            glGenFramebuffers(1, &fbo);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
            fw = w; fh = h;
            atomic_store(&self->force, true);
        }
        uint64_t flags = mpv_render_context_update(self->render);
        if ((flags & MPV_RENDER_UPDATE_FRAME) || atomic_load(&self->force) || self->capturePath) {
            atomic_store(&self->force, false);
            [self renderFbo:(GLint)fbo width:w height:h];
        }
        CGLSetCurrentContext(NULL);
    });
    dispatch_resume(timer);
    self->offscreenTimer = timer;
}

@end

static void on_mpv_update(void *ctx) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)ctx;
    [layer update:NO];
}

#define JNI_FN(name) Java_org_litvin_media_mpv_MpvGlLayerNative_##name

/** Makes the layer and the mpv render context, and puts the layer into the canvas. Returns a handle or 0. */
JNIEXPORT jlong JNICALL JNI_FN(attach)(JNIEnv *env, jclass cls, jobject canvas, jlong mpvHandle) {
    JAWT awt;
    awt.version = JAWT_VERSION_9; // On macOS, version 1.7 and 9 always use CALayers. The CALAYER flag is only for 1.4.
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) { fprintf(stderr, "JAWT_GetAWT failed\n"); return 0; }
    JAWT_DrawingSurface *ds = awt.GetDrawingSurface(env, canvas);
    if (!ds) { fprintf(stderr, "GetDrawingSurface failed\n"); return 0; }
    jint lock = ds->Lock(ds);
    if (lock & JAWT_LOCK_ERROR) { awt.FreeDrawingSurface(ds); fprintf(stderr, "Lock failed\n"); return 0; }
    JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
    id<JAWT_SurfaceLayers> surfaceLayers = (__bridge id<JAWT_SurfaceLayers>)dsi->platformInfo;

    CGLPixelFormatAttribute attrs[] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAAccelerated, kCGLPFADoubleBuffer, kCGLPFAAllowOfflineRenderers,
        kCGLPFAColorSize, (CGLPixelFormatAttribute)24, kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8,
        (CGLPixelFormatAttribute)0,
    };
    CGLPixelFormatObj pix = NULL;
    GLint npix = 0;
    CGLChoosePixelFormat(attrs, &pix, &npix);
    CGLContextObj cgl = NULL;
    CGLCreateContext(pix, NULL, &cgl);
    GLint swapInterval = 0;
    CGLSetParameter(cgl, kCGLCPSwapInterval, &swapInterval);
    CGLEnable(cgl, kCGLCEMPEngine);
    CGLSetCurrentContext(cgl);

    MpvGlLayer *layer = [MpvGlLayer layer];
    layer->pix = pix;
    layer->cgl = cgl;
    layer->queue = dispatch_queue_create("mpv-gl-layer", DISPATCH_QUEUE_SERIAL);
    layer->probeX = 0.5;
    layer->probeY = 0.5;

    mpv_opengl_init_params glInit = { .get_proc_address = get_proc_address };
    int advanced = 1;
    mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, MPV_RENDER_API_TYPE_OPENGL},
        {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &glInit},
        {MPV_RENDER_PARAM_ADVANCED_CONTROL, &advanced},
        {0},
    };
    mpv_render_context *render = NULL;
    int rc = mpv_render_context_create(&render, (mpv_handle *)(intptr_t)mpvHandle, params);
    CGLSetCurrentContext(NULL);
    if (rc < 0) {
        fprintf(stderr, "mpv_render_context_create failed: %s\n", mpv_error_string(rc));
        ds->FreeDrawingSurfaceInfo(dsi); ds->Unlock(ds); awt.FreeDrawingSurface(ds);
        return 0;
    }
    layer->render = render;
    layer.asynchronous = asyncMode();
    layer.opaque = YES;
    layer.needsDisplayOnBoundsChange = YES;
    layer.backgroundColor = CGColorGetConstantColor(kCGColorBlack);
    mpv_render_context_set_update_callback(render, on_mpv_update, (__bridge void *)layer);

    dispatch_async(dispatch_get_main_queue(), ^{
        layer.contentsScale = surfaceLayers.windowLayer.contentsScale;
        surfaceLayers.layer = layer;
        [layer update:YES];
    });
    if (offscreenMode()) [layer startOffscreen];
    ds->FreeDrawingSurfaceInfo(dsi);
    ds->Unlock(ds);
    awt.FreeDrawingSurface(ds);
    return (jlong)(intptr_t)CFBridgingRetain(layer);
}

/** Removes the layer and frees the render context. Call it before mpv_terminate_destroy. */
JNIEXPORT void JNICALL JNI_FN(detach)(JNIEnv *env, jclass cls, jlong handle) {
    MpvGlLayer *layer = (MpvGlLayer *)CFBridgingRelease((void *)(intptr_t)handle);
    dispatch_sync(layer->queue, ^{
        if (layer->offscreenTimer) dispatch_source_cancel(layer->offscreenTimer);
        CGLSetCurrentContext(layer->cgl);
        mpv_render_context_set_update_callback(layer->render, NULL, NULL);
        mpv_render_context_free(layer->render);
        layer->render = NULL;
        CGLSetCurrentContext(NULL);
    });
    dispatch_async(dispatch_get_main_queue(), ^{ [layer removeFromSuperlayer]; });
}

JNIEXPORT jstring JNICALL JNI_FN(debug)(JNIEnv *env, jclass cls, jlong handle) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    NSString *s = [NSString stringWithFormat:@"updates=%lld display=%lld canDraw=%lld canDrawYes=%lld draw=%lld viewport=%lldx%lld frame=%@ super=%@ hidden=%d",
        atomic_load(&dbgUpdates), atomic_load(&dbgDisplay), atomic_load(&dbgCanDraw), atomic_load(&dbgCanDrawYes), atomic_load(&dbgDraw), atomic_load(&dbgViewport) >> 32, atomic_load(&dbgViewport) & 0xffffffff,
        NSStringFromRect(NSRectFromCGRect(layer.frame)), layer.superlayer, layer.hidden];
    return (*env)->NewStringUTF(env, [s UTF8String]);
}

JNIEXPORT jlong JNICALL JNI_FN(nanos)(JNIEnv *env, jclass cls) { return (jlong)now_ns(); }

JNIEXPORT void JNICALL JNI_FN(redraw)(JNIEnv *env, jclass cls, jlong handle) {
    [(__bridge MpvGlLayer *)(void *)(intptr_t)handle update:YES];
}

JNIEXPORT void JNICALL JNI_FN(setProbe)(JNIEnv *env, jclass cls, jlong handle, jboolean on, jdouble x, jdouble y) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    layer->probeX = x;
    layer->probeY = y;
    atomic_store(&layer->probeOn, on);
}

/** [renders, newFrames(unused), renderNsSum, renderNsMax, lateIntervals, intervalSum, intervalCount, probeNs, probePixel, layerW, layerH] */
JNIEXPORT jlongArray JNICALL JNI_FN(stats)(JNIEnv *env, jclass cls, jlong handle) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    CGRect frame = layer.frame;
    jlong values[11] = {
        atomic_load(&layer->renders), atomic_load(&layer->newFrames), atomic_load(&layer->renderNsSum),
        atomic_load(&layer->renderNsMax), atomic_load(&layer->lateIntervals), atomic_load(&layer->intervalSum),
        atomic_load(&layer->intervalCount), atomic_load(&layer->probeNs), atomic_load(&layer->probePixel),
        (jlong)(frame.size.width * layer.contentsScale), (jlong)(frame.size.height * layer.contentsScale),
    };
    jlongArray result = (*env)->NewLongArray(env, 11);
    (*env)->SetLongArrayRegion(env, result, 0, 11, values);
    return result;
}

JNIEXPORT void JNICALL JNI_FN(resetStats)(JNIEnv *env, jclass cls, jlong handle) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    atomic_store(&layer->renders, 0);
    atomic_store(&layer->renderNsSum, 0);
    atomic_store(&layer->renderNsMax, 0);
    atomic_store(&layer->lateIntervals, 0);
    atomic_store(&layer->intervalSum, 0);
    atomic_store(&layer->intervalCount, 0);
    atomic_store(&layer->lastRenderNs, 0);
}

/** Writes the next rendered frame to a PNG file (the pixels of the layer, before macOS composites the window). */
JNIEXPORT jboolean JNICALL JNI_FN(capture)(JNIEnv *env, jclass cls, jlong handle, jstring jpath, jlong timeoutMs) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    const char *cpath = (*env)->GetStringUTFChars(env, jpath, NULL);
    NSString *path = [NSString stringWithUTF8String:cpath];
    (*env)->ReleaseStringUTFChars(env, jpath, cpath);
    atomic_store(&layer->captureDone, false);
    dispatch_sync(layer->queue, ^{ layer->capturePath = path; });
    [layer update:YES];
    uint64_t deadline = now_ns() + (uint64_t)timeoutMs * 1000000ULL;
    while (!atomic_load(&layer->captureDone) && now_ns() < deadline) usleep(2000);
    return atomic_load(&layer->captureDone);
}

/** Q5 test: moves the video layer under the Java window layer and makes the Java window layer not opaque. */
JNIEXPORT void JNICALL JNI_FN(putUnderWindowLayer)(JNIEnv *env, jclass cls, jlong handle, jobject canvas) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    dispatch_async(dispatch_get_main_queue(), ^{
        CALayer *windowLayer = layer.superlayer;
        CALayer *parent = windowLayer.superlayer;
        fprintf(stderr, "windowLayer=%s opaque=%d parent=%s\n", [[windowLayer description] UTF8String],
                windowLayer.opaque, [[parent description] UTF8String]);
        if (!parent) return;
        CGRect frame = [parent convertRect:layer.frame fromLayer:windowLayer];
        [layer removeFromSuperlayer];
        [parent insertSublayer:layer below:windowLayer];
        layer.frame = frame;
        windowLayer.opaque = NO;
    });
}

/** Sets the frame of the layer from the canvas bounds in the content view of the window (points, origin top left). */
JNIEXPORT void JNICALL JNI_FN(setFrame)(JNIEnv *env, jclass cls, jlong handle, jdouble x, jdouble y, jdouble w, jdouble h) {
    MpvGlLayer *layer = (__bridge MpvGlLayer *)(void *)(intptr_t)handle;
    dispatch_async(dispatch_get_main_queue(), ^{
        CALayer *parent = layer.superlayer;
        if (!parent) return;
        CGFloat top = parent.geometryFlipped ? y : parent.bounds.size.height - y - h;
        [CATransaction begin];
        [CATransaction setDisableActions:YES];
        layer.frame = CGRectMake(x, top, w, h);
        layer.contentsScale = parent.contentsScale;
        [CATransaction commit];
        [layer update:YES];
    });
}
