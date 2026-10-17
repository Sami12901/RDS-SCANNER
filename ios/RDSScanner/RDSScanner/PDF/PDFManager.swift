import Foundation
import UIKit
import PDFKit

/// High-speed native PDF generator using Apple's PDFKit and Quartz 2D.
/// Generates clean, zero-watermark, multi-page vector A4 documents ready for sharing.
public final class PDFManager: @unchecked Sendable {
    public static let shared = PDFManager()

    private init() {}

    // Standard ISO A4 dimensions in PDF points (72 points per inch)
    public static let a4Width: CGFloat = 595.2
    public static let a4Height: CGFloat = 841.8

    /// Generates a multi-page A4 PDF file from an array of enhanced page images
    public func generatePDF(from images: [UIImage], title: String) -> URL? {
        guard !images.isEmpty else { return nil }

        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let sanitizedName = cleanTitle.isEmpty ? "Scanned_Document" : cleanTitle.replacingOccurrences(of: "/", with: "_")
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(sanitizedName).pdf")

        // PDF metadata attributes
        let pdfMetaData: [CFString: Any] = [
            kCGPDFContextCreator: "RDS SCANNER (iOS)",
            kCGPDFContextAuthor: "Sami12901",
            kCGPDFContextTitle: sanitizedName,
            kCGPDFContextSubject: "Clean Document Scan"
        ]

        let pdfData = NSMutableData()
        var pageRect = CGRect(x: 0, y: 0, width: Self.a4Width, height: Self.a4Height)

        guard let consumer = CGDataConsumer(data: pdfData as CFMutableData),
              let context = CGContext(consumer: consumer, mediaBox: &pageRect, pdfMetaData as CFDictionary) else {
            return nil
        }

        let margin: CGFloat = 20.0
        let printableWidth = Self.a4Width - (margin * 2)
        let printableHeight = Self.a4Height - (margin * 2)

        for image in images {
            context.beginPage(mediaBox: &pageRect)

            // Compute aspect fit rectangle centered on A4 page
            let imageRatio = image.size.width / image.size.height
            let pageRatio = printableWidth / printableHeight

            var drawWidth: CGFloat
            var drawHeight: CGFloat

            if imageRatio > pageRatio {
                drawWidth = printableWidth
                drawHeight = printableWidth / imageRatio
            } else {
                drawHeight = printableHeight
                drawWidth = printableHeight * imageRatio
            }

            let drawX = margin + (printableWidth - drawWidth) / 2
            let drawY = margin + (printableHeight - drawHeight) / 2
            let drawRect = CGRect(x: drawX, y: drawY, width: drawWidth, height: drawHeight)

            // Flip coordinates for Quartz CGContext (origin at bottom-left)
            context.saveGState()
            context.translateBy(x: 0, y: Self.a4Height)
            context.scaleBy(x: 1.0, y: -1.0)

            let flippedRect = CGRect(x: drawRect.origin.x,
                                     y: Self.a4Height - drawRect.origin.y - drawRect.size.height,
                                     width: drawRect.size.width,
                                     height: drawRect.size.height)

            if let cgImage = image.cgImage {
                context.draw(cgImage, in: flippedRect)
            }
            context.restoreGState()

            context.endPage()
        }

        context.closePDF()

        do {
            try pdfData.write(to: tempURL, options: .atomic)
            return tempURL
        } catch {
            print("Failed to write PDF data to \(tempURL): \(error)")
            return nil
        }
    }
}
