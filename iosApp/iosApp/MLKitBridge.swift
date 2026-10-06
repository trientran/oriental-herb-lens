import ComposeApp
import TensorFlowLite
import UIKit
#if !targetEnvironment(simulator)
import MLKitCommon
import MLKitImageLabeling
import MLKitImageLabelingCustom
import MLKitObjectDetection
import MLKitVision
#endif

#if targetEnvironment(simulator)
// ML Kit has no Apple-silicon simulator binaries (see the Podfile), so identification is
// unavailable here: no labels, no objects. Use a device to try it.
final class MLKitHerbLabeler: NSObject, NativeHerbLabeler {
    func label(image: UIImage, modelPath: String, minConfidence: Float, maxResults: Int32, completion: @escaping ([NativeLabel]?, String?) -> Void) {
        completion([], nil)
    }
}

final class MLKitObjectDetector: NSObject, NativeObjectDetector {
    func detect(image: UIImage, fromCamera: Bool, completion: @escaping ([NativeObject]?, String?) -> Void) {
        completion([], nil)
    }
}

/** Lets every photo through, so sharing can be tried in the simulator. */
final class MLKitGeneralLabeler: NSObject, NativeGeneralLabeler {
    func labels(image: UIImage, minConfidence: Float, completion: @escaping ([String]?, String?) -> Void) {
        completion(["Plant"], nil)
    }
}
#else

/**
 * The herb model through ML Kit's custom image labeler, as on Android. One labeler is kept per
 * model file and options; a downloaded model replaces the file in place, so its modification
 * date is part of the key.
 */
final class MLKitHerbLabeler: NSObject, NativeHerbLabeler {
    private var cached: (key: String, labeler: ImageLabeler)?
    private let lock = NSLock()

    func label(
        image: UIImage,
        modelPath: String,
        minConfidence: Float,
        maxResults: Int32,
        completion: @escaping ([NativeLabel]?, String?) -> Void
    ) {
        let visionImage = VisionImage(image: image)
        visionImage.orientation = image.imageOrientation
        labeler(modelPath: modelPath, minConfidence: minConfidence, maxResults: Int(maxResults))
            .process(visionImage) { labels, error in
                if let error {
                    completion(nil, error.localizedDescription)
                } else {
                    completion((labels ?? []).map { NativeLabel(text: $0.text, confidence: $0.confidence) }, nil)
                }
            }
    }

    private func labeler(modelPath: String, minConfidence: Float, maxResults: Int) -> ImageLabeler {
        lock.lock()
        defer { lock.unlock() }
        let modified = (try? FileManager.default.attributesOfItem(atPath: modelPath)[.modificationDate] as? Date)?
            .timeIntervalSince1970 ?? 0
        let key = "\(modelPath)|\(modified)|\(minConfidence)|\(maxResults)"
        if let cached, cached.key == key { return cached.labeler }
        let options = CustomImageLabelerOptions(localModel: LocalModel(path: modelPath))
        options.confidenceThreshold = NSNumber(value: minConfidence)
        options.maxResultCount = maxResults
        let labeler = ImageLabeler.imageLabeler(options: options)
        cached = (key, labeler)
        return labeler
    }
}

/** ML Kit's built-in object detector: tracking across camera frames, or one photo at a time. */
final class MLKitObjectDetector: NSObject, NativeObjectDetector {
    private lazy var camera = detector(mode: .stream)
    private lazy var photo = detector(mode: .singleImage)

    func detect(image: UIImage, fromCamera: Bool, completion: @escaping ([NativeObject]?, String?) -> Void) {
        let visionImage = VisionImage(image: image)
        visionImage.orientation = image.imageOrientation
        (fromCamera ? camera : photo).process(visionImage) { objects, error in
            if let error {
                completion(nil, error.localizedDescription)
                return
            }
            completion((objects ?? []).map { object in
                NativeObject(
                    left: object.frame.minX,
                    top: object.frame.minY,
                    right: object.frame.maxX,
                    bottom: object.frame.maxY,
                    trackingId: object.trackingID?.int32Value ?? -1
                )
            }, nil)
        }
    }

    private func detector(mode: ObjectDetectorMode) -> ObjectDetector {
        let options = ObjectDetectorOptions()
        options.detectorMode = mode
        options.shouldEnableMultipleObjects = true
        return ObjectDetector.objectDetector(options: options)
    }
}
/** ML Kit's general labeller (bundled model), for the check that a photo to share shows a plant. */
final class MLKitGeneralLabeler: NSObject, NativeGeneralLabeler {
    private var cached: (confidence: Float, labeler: ImageLabeler)?

    func labels(image: UIImage, minConfidence: Float, completion: @escaping ([String]?, String?) -> Void) {
        let visionImage = VisionImage(image: image)
        visionImage.orientation = image.imageOrientation
        labeler(minConfidence).process(visionImage) { labels, error in
            if let error {
                completion(nil, error.localizedDescription)
            } else {
                completion((labels ?? []).map(\.text), nil)
            }
        }
    }

    private func labeler(_ confidence: Float) -> ImageLabeler {
        if let cached, cached.confidence == confidence { return cached.labeler }
        let options = ImageLabelerOptions()
        options.confidenceThreshold = NSNumber(value: confidence)
        let labeler = ImageLabeler.imageLabeler(options: options)
        cached = (confidence, labeler)
        return labeler
    }
}
#endif

/**
 * User-trained models' backbone (plan Phase 7) through LiteRT (TensorFlowLiteSwift): 224 × 224
 * RGB in [0, 1], as the MediaPipe image embedders expect; returns the first output's float32
 * bytes. One interpreter per model, kept until released; runs off the main thread.
 */
final class LiteRTEmbedder: NSObject, NativeEmbedder {
    private let queue = DispatchQueue(label: "litert.embedder")
    private var interpreters: [String: Interpreter] = [:]
    private static let size = 224

    func embed(image: UIImage, modelPath: String, completion: @escaping (Data?, String?) -> Void) {
        queue.async {
            do {
                let interpreter = try self.interpreter(modelPath)
                guard let input = Self.pixels(image) else { return completion(nil, "Image unreadable") }
                try interpreter.copy(input, toInputAt: 0)
                try interpreter.invoke()
                completion(try interpreter.output(at: 0).data, nil)
            } catch {
                completion(nil, error.localizedDescription)
            }
        }
    }

    func release(modelPath: String) {
        queue.async { self.interpreters[modelPath] = nil }
    }

    private func interpreter(_ path: String) throws -> Interpreter {
        if let cached = interpreters[path] { return cached }
        var options = Interpreter.Options()
        options.threadCount = 4
        let interpreter = try Interpreter(modelPath: path, options: options)
        try interpreter.allocateTensors()
        interpreters[path] = interpreter
        return interpreter
    }

    /** The image drawn at 224 × 224 (upright), as float32 RGB in [0, 1]. */
    private static func pixels(_ image: UIImage) -> Data? {
        let side = size
        var rgba = [UInt8](repeating: 0, count: side * side * 4)
        guard let context = CGContext(
            data: &rgba, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else { return nil }
        UIGraphicsPushContext(context)
        // UIKit draws upside down in a bare CGContext: flip it, then draw honouring the orientation
        context.translateBy(x: 0, y: CGFloat(side))
        context.scaleBy(x: 1, y: -1)
        image.draw(in: CGRect(x: 0, y: 0, width: side, height: side))
        UIGraphicsPopContext()
        var floats = [Float](repeating: 0, count: side * side * 3)
        for i in 0..<(side * side) {
            floats[i * 3] = Float(rgba[i * 4]) / 255
            floats[i * 3 + 1] = Float(rgba[i * 4 + 1]) / 255
            floats[i * 3 + 2] = Float(rgba[i * 4 + 2]) / 255
        }
        return floats.withUnsafeBufferPointer { Data(buffer: $0) }
    }
}
