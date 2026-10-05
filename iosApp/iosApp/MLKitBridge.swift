import ComposeApp
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
