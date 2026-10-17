import SwiftUI
import PDFKit
import UniformTypeIdentifiers

/// Adobe Acrobat PDF Tools Suite for iOS.
public struct PdfToolsView: View {
    @State private var showDocumentPicker = false
    @State private var activeToolTitle = ""
    @State private var statusMessage = ""
    @State private var isProcessing = false
    @State private var generatedPdfURL: URL?
    @State private var showReader = false

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Header Banner
                    HStack(spacing: 16) {
                        Image(systemName: "doc.text.fill")
                            .font(.system(size: 32))
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(Color(red: 0.0, green: 0.66, blue: 0.42))
                            .clipShape(RoundedRectangle(cornerRadius: 14))

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Acrobat Power Suite")
                                .font(.headline)
                                .fontWeight(.bold)
                            Text("Fast, offline PDF utilities directly on your device")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }
                    .padding()
                    .background(Color(red: 0.0, green: 0.66, blue: 0.42).opacity(0.12))
                    .cornerRadius(16)

                    // Tool Cards
                    PdfToolRow(
                        title: "Merge PDFs",
                        description: "Combine multiple PDF files into one continuous document",
                        icon: "arrow.triangle.merge",
                        tint: .blue
                    ) {
                        activeToolTitle = "Merge"
                        showDocumentPicker = true
                    }

                    PdfToolRow(
                        title: "Split PDF",
                        description: "Extract pages or separate a document into individual PDF pages",
                        icon: "arrow.triangle.branch",
                        tint: .orange
                    ) {
                        activeToolTitle = "Split"
                        showDocumentPicker = true
                    }

                    PdfToolRow(
                        title: "Compress PDF",
                        description: "Reduce document file size with high visual quality for easy sharing",
                        icon: "arrow.down.right.and.arrow.up.left",
                        tint: .green
                    ) {
                        activeToolTitle = "Compress"
                        showDocumentPicker = true
                    }

                    PdfToolRow(
                        title: "Add Watermark",
                        description: "Apply custom text watermarks across all pages",
                        icon: "drop.fill",
                        tint: .cyan
                    ) {
                        activeToolTitle = "Watermark"
                        showDocumentPicker = true
                    }

                    PdfToolRow(
                        title: "Images to PDF",
                        description: "Convert photos and gallery images into a single multi-page PDF",
                        icon: "photo.on.rectangle.angled",
                        tint: .purple
                    ) {
                        activeToolTitle = "ImagesToPdf"
                        showDocumentPicker = true
                    }

                    PdfToolRow(
                        title: "PDF to Images",
                        description: "Export high-resolution JPEG images of every page in the PDF",
                        icon: "photo.stack",
                        tint: .red
                    ) {
                        activeToolTitle = "PdfToImages"
                        showDocumentPicker = true
                    }
                }
                .padding()
            }
            .navigationTitle("PDF Tools")
            .sheet(isPresented: $showDocumentPicker) {
                DocumentPicker(activeTool: activeToolTitle) { url in
                    if let url = url {
                        handleSelectedDocument(url: url)
                    }
                }
            }
            .sheet(isPresented: $showReader) {
                if let url = generatedPdfURL {
                    PdfReaderView(pdfURL: url, title: url.lastPathComponent)
                }
            }
        }
    }

    private func handleSelectedDocument(url: URL) {
        if activeToolTitle == "Compress" {
            let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("Compressed_\(Date().timeIntervalSince1970).pdf")
            if PdfToolsManager.shared.compressPdf(url: url, outputURL: outURL) {
                generatedPdfURL = outURL
                showReader = true
            }
        } else if activeToolTitle == "Watermark" {
            let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("Watermarked_\(Date().timeIntervalSince1970).pdf")
            if PdfToolsManager.shared.watermarkPdf(url: url, outputURL: outURL, watermarkText: "CONFIDENTIAL") {
                generatedPdfURL = outURL
                showReader = true
            }
        } else {
            generatedPdfURL = url
            showReader = true
        }
    }
}

struct PdfToolRow: View {
    let title: String
    let description: String
    let icon: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(tint)
                    .frame(width: 48, height: 48)
                    .background(tint.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(description)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.footnote)
            }
            .padding()
            .background(Color(uiColor: .secondarySystemGroupedBackground))
            .cornerRadius(14)
        }
    }
}

struct DocumentPicker: UIViewControllerRepresentable {
    let activeTool: String
    let onPick: (URL?) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = activeTool == "ImagesToPdf" ? [.image] : [.pdf]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let parent: DocumentPicker
        init(_ parent: DocumentPicker) { self.parent = parent }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            parent.onPick(urls.first)
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.onPick(nil)
        }
    }
}
