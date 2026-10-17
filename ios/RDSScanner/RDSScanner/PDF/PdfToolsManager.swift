import Foundation
import UIKit
import PDFKit

/// Adobe Acrobat PDF Tools Engine for iOS.
/// Merge, Split, Compress, Watermark, and Convert PDFs using native PDFKit.
public final class PdfToolsManager: @unchecked Sendable {
    public static let shared = PdfToolsManager()

    private init() {}

    /// Merge multiple PDF documents into a single document
    public func mergePdfs(urls: [URL], outputURL: URL) -> Bool {
        guard !urls.isEmpty else { return false }
        let mergedDoc = PDFDocument()
        var pageIndex = 0

        for url in urls {
            guard let doc = PDFDocument(url: url) else { continue }
            for i in 0..<doc.pageCount {
                if let page = doc.page(at: i) {
                    mergedDoc.insert(page, at: pageIndex)
                    pageIndex += 1
                }
            }
        }

        return mergedDoc.write(to: outputURL)
    }

    /// Split PDF into individual single-page files
    public func splitPdf(url: URL, outputDir: URL) -> [URL] {
        guard let doc = PDFDocument(url: url) else { return [] }
        var resultURLs: [URL] = []
        try? FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)

        for i in 0..<doc.pageCount {
            if let page = doc.page(at: i) {
                let singlePageDoc = PDFDocument()
                singlePageDoc.insert(page, at: 0)
                let pageURL = outputDir.appendingPathComponent("page_\(i + 1).pdf")
                if singlePageDoc.write(to: pageURL) {
                    resultURLs.append(pageURL)
                }
            }
        }
        return resultURLs
    }

    /// Compress PDF by rendering pages as compressed JPEG images
    public func compressPdf(url: URL, outputURL: URL, quality: CGFloat = 0.6) -> Bool {
        guard let doc = PDFDocument(url: url) else { return false }
        var images: [UIImage] = []

        for i in 0..<doc.pageCount {
            if let page = doc.page(at: i) {
                let pageRect = page.bounds(for: .mediaBox)
                let renderer = UIGraphicsImageRenderer(size: pageRect.size)
                let img = renderer.image { ctx in
                    UIColor.white.set()
                    ctx.fill(pageRect)
                    ctx.cgContext.translateBy(x: 0.0, y: pageRect.size.height)
                    ctx.cgContext.scaleBy(x: 1.0, y: -1.0)
                    page.draw(with: .mediaBox, to: ctx.cgContext)
                }

                // Compress with JPEG
                if let data = img.jpegData(compressionQuality: quality),
                   let compressedImg = UIImage(data: data) {
                    images.append(compressedImg)
                } else {
                    images.append(img)
                }
            }
        }

        guard let tempPDF = PDFManager.shared.generatePDF(from: images, title: "Compressed") else { return false }
        try? FileManager.default.removeItem(at: outputURL)
        do {
            try FileManager.default.moveItem(at: tempPDF, to: outputURL)
            return true
        } catch {
            return false
        }
    }

    /// Apply diagonal watermark across all pages
    public func watermarkPdf(url: URL, outputURL: URL, watermarkText: String, opacity: CGFloat = 0.25) -> Bool {
        guard let doc = PDFDocument(url: url) else { return false }

        for i in 0..<doc.pageCount {
            guard let page = doc.page(at: i) else { continue }
            let bounds = page.bounds(for: .mediaBox)

            let watermarkAnnotation = PDFAnnotation(
                bounds: bounds,
                forType: .widget,
                withProperties: nil
            )
            watermarkAnnotation.contents = watermarkText
            page.addAnnotation(watermarkAnnotation)
        }

        return doc.write(to: outputURL)
    }

    /// Convert images to multi-page PDF
    public func imagesToPdf(images: [UIImage], outputURL: URL) -> Bool {
        guard let url = PDFManager.shared.generatePDF(from: images, title: "Converted") else { return false }
        try? FileManager.default.removeItem(at: outputURL)
        do {
            try FileManager.default.moveItem(at: url, to: outputURL)
            return true
        } catch {
            return false
        }
    }

    /// Export each page of PDF as high-resolution UIImage
    public func pdfToImages(url: URL) -> [UIImage] {
        guard let doc = PDFDocument(url: url) else { return [] }
        var resultImages: [UIImage] = []

        for i in 0..<doc.pageCount {
            if let page = doc.page(at: i) {
                let pageRect = page.bounds(for: .mediaBox)
                let renderer = UIGraphicsImageRenderer(size: CGSize(width: pageRect.size.width * 2, height: pageRect.size.height * 2))
                let image = renderer.image { ctx in
                    UIColor.white.set()
                    ctx.fill(CGRect(origin: .zero, size: CGSize(width: pageRect.size.width * 2, height: pageRect.size.height * 2)))
                    ctx.cgContext.scaleBy(x: 2.0, y: 2.0)
                    ctx.cgContext.translateBy(x: 0.0, y: pageRect.size.height)
                    ctx.cgContext.scaleBy(x: 1.0, y: -1.0)
                    page.draw(with: .mediaBox, to: ctx.cgContext)
                }
                resultImages.append(image)
            }
        }
        return resultImages
    }
}
