import Foundation
import UIKit
import Combine

/// Manages local sandboxed persistence of documents, raw images, and enhanced images.
/// 100% on-device, offline, and private.
@MainActor
public final class DocumentStorageManager: ObservableObject {
    public static let shared = DocumentStorageManager()

    @Published public private(set) var documents: [ScannedDocument] = []

    private let fileManager = FileManager.default
    private let baseDirectory: URL
    private let metadataFileURL: URL

    public init() {
        let paths = fileManager.urls(for: .documentDirectory, in: .userDomainMask)
        let docDir = paths[0].appendingPathComponent("RDSScanner", isDirectory: true)
        self.baseDirectory = docDir
        self.metadataFileURL = docDir.appendingPathComponent("documents.json")

        // Ensure root directory exists
        try? fileManager.createDirectory(at: docDir, withIntermediateDirectories: true)
        loadDocuments()
    }

    // MARK: - Document Loading & Persistence

    public func loadDocuments() {
        guard fileManager.fileExists(atPath: metadataFileURL.path) else {
            self.documents = []
            return
        }

        do {
            let data = try Data(contentsOf: metadataFileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let loaded = try decoder.decode([ScannedDocument].self, from: data)
            self.documents = loaded.sorted { $0.updatedAt > $1.updatedAt }
        } catch {
            print("Error loading documents: \(error)")
            self.documents = []
        }
    }

    private func saveMetadata() {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(documents)
            try data.write(to: metadataFileURL, options: .atomic)
        } catch {
            print("Error saving document metadata: \(error)")
        }
    }

    // MARK: - Image File Management

    public func fileURL(for fileName: String) -> URL {
        baseDirectory.appendingPathComponent(fileName)
    }

    public func loadImage(named fileName: String) -> UIImage? {
        let url = fileURL(for: fileName)
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    public func saveImage(_ image: UIImage, fileName: String, compressionQuality: CGFloat = 0.92) -> Bool {
        let url = fileURL(for: fileName)
        guard let data = image.jpegData(compressionQuality: compressionQuality) else { return false }
        do {
            try data.write(to: url, options: .atomic)
            return true
        } catch {
            print("Failed to save image \(fileName): \(error)")
            return false
        }
    }

    public func deleteFile(named fileName: String) {
        let url = fileURL(for: fileName)
        try? fileManager.removeItem(at: url)
    }

    // MARK: - Document CRUD

    /// Creates a new document with raw images captured by the camera.
    /// Stores both the untouched original raw image and the enhanced version.
    public func createDocument(name: String, scannedImages: [UIImage]) -> ScannedDocument {
        let docId = UUID().uuidString
        var pages: [ScannedPage] = []

        for (index, rawImage) in scannedImages.enumerated() {
            let pageId = UUID().uuidString
            let origFileName = "orig_\(docId)_\(pageId).jpg"
            let pageFileName = "page_\(docId)_\(pageId).jpg"

            // Save raw original capture
            _ = saveImage(rawImage, fileName: origFileName)

            // Process default Magic Color enhancement
            let enhanced = ImageProcessor.shared.applyMagicColor(to: rawImage)
            _ = saveImage(enhanced, fileName: pageFileName)

            let page = ScannedPage(
                id: pageId,
                pageIndex: index,
                originalFileName: origFileName,
                enhancedFileName: pageFileName,
                filterType: .magicColor,
                rotationDegrees: 0,
                dateAdded: Date()
            )
            pages.append(page)
        }

        let document = ScannedDocument(
            id: docId,
            name: name.isEmpty ? "Doc_\(Int(Date().timeIntervalSince1970))" : name,
            pages: pages,
            createdAt: Date(),
            updatedAt: Date()
        )

        documents.insert(document, at: 0)
        saveMetadata()
        return document
    }

    /// Non-destructively changes the filter on a page by reading the original raw capture.
    public func updatePageFilter(documentId: String, pageId: String, filter: FilterType) {
        guard let docIndex = documents.firstIndex(where: { $0.id == documentId }),
              let pageIndex = documents[docIndex].pages.firstIndex(where: { $0.id == pageId }) else {
            return
        }

        let page = documents[docIndex].pages[pageIndex]

        // Load the untouched pristine original raw capture
        guard let rawOriginal = loadImage(named: page.originalFileName) else { return }

        // Apply rotation if needed
        var baseImage = rawOriginal
        if page.rotationDegrees != 0 {
            baseImage = ImageProcessor.shared.rotate(image: baseImage, degrees: page.rotationDegrees)
        }

        // Apply selected filter
        let processed: UIImage
        switch filter {
        case .original:
            processed = baseImage
        case .magicColor:
            processed = ImageProcessor.shared.applyMagicColor(to: baseImage)
        case .blackAndWhite:
            processed = ImageProcessor.shared.applyBlackAndWhite(to: baseImage)
        case .grayscale:
            processed = ImageProcessor.shared.applyGrayscale(to: baseImage)
        case .lighten:
            processed = ImageProcessor.shared.applyLighten(to: baseImage)
        }

        // Overwrite enhanced file without touching original file
        _ = saveImage(processed, fileName: page.enhancedFileName)

        documents[docIndex].pages[pageIndex].filterType = filter
        documents[docIndex].updatedAt = Date()
        saveMetadata()
    }

    /// Rotates a page by 90 degrees clockwise non-destructively
    public func rotatePage(documentId: String, pageId: String) {
        guard let docIndex = documents.firstIndex(where: { $0.id == documentId }),
              let pageIndex = documents[docIndex].pages.firstIndex(where: { $0.id == pageId }) else {
            return
        }

        var page = documents[docIndex].pages[pageIndex]
        page.rotationDegrees = (page.rotationDegrees + 90) % 360
        documents[docIndex].pages[pageIndex] = page

        // Re-process from original with the new rotation
        updatePageFilter(documentId: documentId, pageId: pageId, filter: page.filterType)
    }

    /// Replaces the page's enhanced image with a custom composed image (e.g. after signature stamping)
    public func updatePageEnhancedImage(documentId: String, pageId: String, newImage: UIImage) {
        guard let docIndex = documents.firstIndex(where: { $0.id == documentId }),
              let pageIndex = documents[docIndex].pages.firstIndex(where: { $0.id == pageId }) else {
            return
        }

        let page = documents[docIndex].pages[pageIndex]
        _ = saveImage(newImage, fileName: page.enhancedFileName)
        documents[docIndex].updatedAt = Date()
        saveMetadata()
    }

    /// Appends new scanned pages to an existing document
    public func appendPages(to documentId: String, images: [UIImage]) {
        guard let docIndex = documents.firstIndex(where: { $0.id == documentId }) else { return }

        var currentPages = documents[docIndex].pages
        let startingIndex = currentPages.count

        for (offset, rawImage) in images.enumerated() {
            let pageId = UUID().uuidString
            let origFileName = "orig_\(documentId)_\(pageId).jpg"
            let pageFileName = "page_\(documentId)_\(pageId).jpg"

            _ = saveImage(rawImage, fileName: origFileName)
            let enhanced = ImageProcessor.shared.applyMagicColor(to: rawImage)
            _ = saveImage(enhanced, fileName: pageFileName)

            let page = ScannedPage(
                id: pageId,
                pageIndex: startingIndex + offset,
                originalFileName: origFileName,
                enhancedFileName: pageFileName,
                filterType: .magicColor,
                rotationDegrees: 0,
                dateAdded: Date()
            )
            currentPages.append(page)
        }

        documents[docIndex].pages = currentPages
        documents[docIndex].updatedAt = Date()
        saveMetadata()
    }

    /// Deletes a single page from a document
    public func deletePage(documentId: String, pageId: String) {
        guard let docIndex = documents.firstIndex(where: { $0.id == documentId }) else { return }

        if let page = documents[docIndex].pages.first(where: { $0.id == pageId }) {
            deleteFile(named: page.originalFileName)
            deleteFile(named: page.enhancedFileName)
        }

        documents[docIndex].pages.removeAll { $0.id == pageId }

        // Re-index remaining pages
        for i in 0..<documents[docIndex].pages.count {
            documents[docIndex].pages[i].pageIndex = i
        }

        if documents[docIndex].pages.isEmpty {
            documents.remove(at: docIndex)
        } else {
            documents[docIndex].updatedAt = Date()
        }

        saveMetadata()
    }

    /// Deletes an entire document and removes all page images from disk
    public func deleteDocument(id: String) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        let doc = documents[index]

        for page in doc.pages {
            deleteFile(named: page.originalFileName)
            deleteFile(named: page.enhancedFileName)
        }

        documents.remove(at: index)
        saveMetadata()
    }

    /// Renames a document
    public func renameDocument(id: String, newName: String) {
        guard let index = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[index].name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        documents[index].updatedAt = Date()
        saveMetadata()
    }
}
