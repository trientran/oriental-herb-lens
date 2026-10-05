package com.uri.lee.dl.core.ml

import platform.UIKit.UIImage

/**
 * ML Kit for iOS is a CocoaPods library with no Kotlin bindings, so the iOS app implements these
 * two interfaces in Swift (iosApp/iosApp/MLKitBridge.swift) and hands them to `startKoinIos`.
 * Each calls [completion] exactly once, from any thread: results, or null and an error message.
 */
interface NativeHerbLabeler {
    /** Custom image labelling with the .tflite at [modelPath], which carries its labels as metadata. */
    fun label(
        image: UIImage,
        modelPath: String,
        minConfidence: Float,
        maxResults: Int,
        completion: (List<NativeLabel>?, String?) -> Unit,
    )
}

class NativeLabel(val text: String, val confidence: Float)

interface NativeGeneralLabeler {
    /** ML Kit's general image labeller (hundreds of everyday labels), for the upload plant check. */
    fun labels(image: UIImage, minConfidence: Float, completion: (List<String>?, String?) -> Unit)
}

interface NativeObjectDetector {
    /** ML Kit's built-in object detector: stream mode with tracking for camera frames, single-image mode otherwise. */
    fun detect(image: UIImage, fromCamera: Boolean, completion: (List<NativeObject>?, String?) -> Unit)
}

/** A detected object's frame in image pixels; [trackingId] is -1 when there is none. */
class NativeObject(val left: Double, val top: Double, val right: Double, val bottom: Double, val trackingId: Int)
