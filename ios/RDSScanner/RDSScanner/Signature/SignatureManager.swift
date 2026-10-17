import Foundation
import UIKit
import Combine

/// Manages signature library storage, transparent stroke auto-cropping, and ink recoloring.
@MainActor
public final class SignatureManager: ObservableObject {
    public static let shared = SignatureManager()

    @Published public private(set) var savedSignatureFiles: [String] = []

    private let fileManager = FileManager.default
    private let signaturesDirectory: URL

    public init() {
        let paths = fileManager.urls(for: .documentDirectory, in: .userDomainMask)
        let dir = paths[0].appendingPathComponent("RDSScanner/Signatures", isDirectory: true)
        self.signaturesDirectory = dir

        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        loadSavedSignatures()
    }

    public func loadSavedSignatures() {
        guard let files = try? fileManager.contentsOfDirectory(atPath: signaturesDirectory.path) else {
            self.savedSignatureFiles = []
            return
        }

        self.savedSignatureFiles = files
            .filter { $0.hasSuffix(".png") }
            .sorted { file1, file2 in
                let u1 = signaturesDirectory.appendingPathComponent(file1)
                let u2 = signaturesDirectory.appendingPathComponent(file2)
                let date1 = (try? fileManager.attributesOfItem(atPath: u1.path)[.creationDate] as? Date) ?? Date.distantPast
                let date2 = (try? fileManager.attributesOfItem(atPath: u2.path)[.creationDate] as? Date) ?? Date.distantPast
                return date1 > date2
            }
    }

    public func fileURL(for fileName: String) -> URL {
        signaturesDirectory.appendingPathComponent(fileName)
    }

    public func loadSignature(named fileName: String) -> UIImage? {
        let url = fileURL(for: fileName)
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    /// Saves a newly drawn signature transparent PNG, auto-cropping bounding box
    public func saveSignature(_ image: UIImage) -> UIImage? {
        guard let cropped = cropToBoundingBox(image: image) else { return nil }

        let fileName = "sig_\(Int(Date().timeIntervalSince1970 * 1000)).png"
        let url = fileURL(for: fileName)

        if let pngData = cropped.pngData() {
            try? pngData.write(to: url, options: .atomic)
            loadSavedSignatures()
            return cropped
        }
        return nil
    }

    public func deleteSignature(named fileName: String) {
        let url = fileURL(for: fileName)
        try? fileManager.removeItem(at: url)
        loadSavedSignatures()
    }

    // MARK: - Auto-Crop Transparent Bounding Box

    /// Scans pixel alpha values and crops tightly to stroke boundaries with minimal padding
    public func cropToBoundingBox(image: UIImage, padding: CGFloat = 8.0) -> UIImage? {
        guard let cgImage = image.cgImage else { return nil }

        let width = cgImage.width
        let height = cgImage.height

        guard let colorSpace = cgImage.colorSpace else { return nil }
        var rawData = [UInt8](repeating: 0, count: width * height * 4)

        guard let context = CGContext(
            data: &rawData,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ) else { return nil }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var hasDrawnPixel = false

        for y in 0..<height {
            for x in 0..<width {
                let pixelIndex = (y * width + x) * 4
                let alpha = rawData[pixelIndex + 3]
                if alpha > 15 {
                    hasDrawnPixel = true
                    if x < minX { minX = x }
                    if x > maxX { maxX = x }
                    if y < minY { minY = y }
                    if y > maxY { maxY = y }
                }
            }
        }

        guard hasDrawnPixel else { return image }

        let pad = Int(padding)
        let cropX = max(0, minX - pad)
        let cropY = max(0, minY - pad)
        let cropWidth = min(width - cropX, (maxX - minX) + (pad * 2))
        let cropHeight = min(height - cropY, (maxY - minY) + (pad * 2))

        guard cropWidth > 0 && cropHeight > 0,
              let croppedCG = cgImage.cropping(to: CGRect(x: cropX, y: cropY, width: cropWidth, height: cropHeight)) else {
            return image
        }

        return UIImage(cgImage: croppedCG, scale: image.scale, orientation: image.imageOrientation)
    }

    // MARK: - Dynamic Ink Recoloring

    /// Dynamically recolors a transparent signature stroke while preserving anti-aliased edge alphas
    public func recolor(image: UIImage, to color: UIColor) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = false

        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: image.size)
            let cg = context.cgContext

            // Draw image first
            image.draw(in: rect)

            // Clip to source alpha and fill with new color
            cg.setBlendMode(.sourceIn)
            cg.setFillColor(color.cgColor)
            cg.fill(rect)
        }
    }
}
