import Foundation
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

/// Hardware-accelerated GPU image enhancement engine powered by Metal and CoreImage.
/// Replicates CamScanner's Magic Color, crisp B&W, Grayscale, and Lighten modes.
public final class ImageProcessor: @unchecked Sendable {
    public static let shared = ImageProcessor()

    private let ciContext: CIContext

    private init() {
        // Backed by Metal GPU context for instant real-time processing
        if let metalDevice = MTLCreateSystemDefaultDevice() {
            self.ciContext = CIContext(mtlDevice: metalDevice)
        } else {
            self.ciContext = CIContext(options: [
                .useSoftwareRenderer: false,
                .priorityRequestLow: false
            ])
        }
    }

    // MARK: - CamScanner Filters

    /// Magic Color:
    /// 1. Uses Apple's CIDocumentEnhancer for intelligent paper whitening and text de-shadowing.
    /// 2. Boosts ink saturation (1.25x) to preserve vibrant signature and stamp colors (blue, red, green).
    /// 3. Balances contrast and exposure to eliminate blown-out highlights.
    public func applyMagicColor(to image: UIImage) -> UIImage {
        guard let ciInput = CIImage(image: image) else { return image }

        // Step 1: Document Background Whitening & Text Sharpening
        var outputImage = ciInput
        if let docEnhancer = CIFilter(name: "CIDocumentEnhancer") {
            docEnhancer.setValue(ciInput, forKey: kCIInputImageKey)
            docEnhancer.setValue(1.5, forKey: "inputAmount")
            if let enhanced = docEnhancer.outputImage {
                outputImage = enhanced
            }
        }

        // Step 2: Ink Vibrance Preservation & Contrast Balancing
        let colorControls = CIFilter.colorControls()
        colorControls.inputImage = outputImage
        colorControls.contrast = 1.18
        colorControls.saturation = 1.25
        colorControls.brightness = 0.02

        if let balanced = colorControls.outputImage {
            outputImage = balanced
        }

        // Step 3: Highlight Protection / Shadow Recovery
        let shadowAdjust = CIFilter.highlightShadowAdjust()
        shadowAdjust.inputImage = outputImage
        shadowAdjust.shadowAmount = 0.35
        shadowAdjust.highlightAmount = 0.90

        if let finalCI = shadowAdjust.outputImage {
            outputImage = finalCI
        }

        return render(ciImage: outputImage, originalOrientation: image.imageOrientation) ?? image
    }

    /// Black & White:
    /// High-contrast document binarization producing crisp black text on pure white paper
    /// with zero corner shadow artifacts.
    public func applyBlackAndWhite(to image: UIImage) -> UIImage {
        guard let ciInput = CIImage(image: image) else { return image }

        // Document enhancer first to clear paper background
        var current = ciInput
        if let docEnhancer = CIFilter(name: "CIDocumentEnhancer") {
            docEnhancer.setValue(ciInput, forKey: kCIInputImageKey)
            docEnhancer.setValue(2.0, forKey: "inputAmount")
            if let out = docEnhancer.outputImage {
                current = out
            }
        }

        // Convert to high-contrast monochrome
        let monoFilter = CIFilter.colorMonochrome()
        monoFilter.inputImage = current
        monoFilter.color = CIColor(red: 0.5, green: 0.5, blue: 0.5)
        monoFilter.intensity = 1.0

        if let mono = monoFilter.outputImage {
            current = mono
        }

        // Steepen contrast curve for pure black on pure white
        let contrastFilter = CIFilter.colorControls()
        contrastFilter.inputImage = current
        contrastFilter.contrast = 2.4
        contrastFilter.brightness = 0.08

        let finalCI = contrastFilter.outputImage ?? current
        return render(ciImage: finalCI, originalOrientation: image.imageOrientation) ?? image
    }

    /// Grayscale:
    /// Smooth tonal grayscale preserving full photographic fidelity without color casts.
    public func applyGrayscale(to image: UIImage) -> UIImage {
        guard let ciInput = CIImage(image: image) else { return image }

        let monoFilter = CIFilter.photoEffectMono()
        monoFilter.inputImage = ciInput

        var current = monoFilter.outputImage ?? ciInput

        let controls = CIFilter.colorControls()
        controls.inputImage = current
        controls.contrast = 1.10
        controls.brightness = 0.02

        let finalCI = controls.outputImage ?? current
        return render(ciImage: finalCI, originalOrientation: image.imageOrientation) ?? image
    }

    /// Lighten:
    /// Brightens dark captures while retaining text legibility.
    public func applyLighten(to image: UIImage) -> UIImage {
        guard let ciInput = CIImage(image: image) else { return image }

        let shadowFilter = CIFilter.highlightShadowAdjust()
        shadowFilter.inputImage = ciInput
        shadowFilter.shadowAmount = 0.65
        shadowFilter.highlightAmount = 0.85

        var current = shadowFilter.outputImage ?? ciInput

        let controls = CIFilter.colorControls()
        controls.inputImage = current
        controls.brightness = 0.06
        controls.contrast = 1.08

        let finalCI = controls.outputImage ?? current
        return render(ciImage: finalCI, originalOrientation: image.imageOrientation) ?? image
    }

    // MARK: - Transformations

    /// Rotates an image by the specified degrees (90, 180, 270)
    public func rotate(image: UIImage, degrees: Int) -> UIImage {
        let radians = CGFloat(degrees) * .pi / 180.0
        var newSize = CGRect(origin: .zero, size: image.size)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral.size
        newSize.width = round(newSize.width)
        newSize.height = round(newSize.height)

        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { context in
            let cgContext = context.cgContext
            cgContext.translateBy(x: newSize.width / 2, y: newSize.height / 2)
            cgContext.rotate(by: radians)
            image.draw(in: CGRect(
                x: -image.size.width / 2,
                y: -image.size.height / 2,
                width: image.size.width,
                height: image.size.height
            ))
        }
    }

    // MARK: - Internal GPU Renderer

    private func render(ciImage: CIImage, originalOrientation: UIImage.Orientation) -> UIImage? {
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage, scale: 1.0, orientation: originalOrientation)
    }
}
