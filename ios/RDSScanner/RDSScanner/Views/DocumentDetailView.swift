import SwiftUI

/// Detailed view of a scanned document supporting non-destructive filter toggling,
/// page navigation, rotation, digital signature burning, and multi-page PDF export.
public struct DocumentDetailView: View {
    public let documentId: String

    @StateObject private var storageManager = DocumentStorageManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var currentPageIndex = 0
    @State private var showingAddPageScanner = false
    @State private var showingSignatureDialog = false
    @State private var signatureToPlace: UIImage?
    @State private var isPlacingSignature = false
    @State private var activeShareURL: URL?
    @State private var showingDeleteAlert = false

    private let emeraldColor = Color(red: 0.0, green: 0.66, blue: 0.42)

    private var document: ScannedDocument? {
        storageManager.documents.first { $0.id == documentId }
    }

    private var currentPage: ScannedPage? {
        guard let doc = document, !doc.pages.isEmpty, currentPageIndex < doc.pages.count else { return nil }
        return doc.pages[currentPageIndex]
    }

    public init(documentId: String) {
        self.documentId = documentId
    }

    public var body: some View {
        ZStack {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()

            if let doc = document, !doc.pages.isEmpty {
                VStack(spacing: 0) {
                    // Top Page Indicator & Quick Actions
                    HStack {
                        Text("Page \(currentPageIndex + 1) of \(doc.pages.count)")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.secondary)

                        Spacer()

                        Button(action: {
                            if let page = currentPage {
                                storageManager.rotatePage(documentId: documentId, pageId: page.id)
                            }
                        }) {
                            Label("Rotate", systemImage: "rotate.right")
                                .font(.footnote)
                                .foregroundColor(emeraldColor)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)

                    // Page Image Carousel
                    TabView(selection: $currentPageIndex) {
                        ForEach(Array(doc.pages.enumerated()), id: \.element.id) { index, page in
                            ZStack {
                                if let image = storageManager.loadImage(named: page.enhancedFileName) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFit()
                                        .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 8)
                                } else {
                                    ProgressView()
                                }
                            }
                            .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))

                    // Non-Destructive Filter Selection Bar
                    if let page = currentPage {
                        filterSelectionBar(for: page)
                    }

                    // Bottom Action Toolbar
                    bottomActionToolbar
                }

                // Interactive Signature Overlay Mode
                if isPlacingSignature, let page = currentPage,
                   let baseImage = storageManager.loadImage(named: page.enhancedFileName) {
                    SignatureOverlayView(
                        baseImage: baseImage,
                        signatureImage: $signatureToPlace,
                        onApply: { compositeImage in
                            storageManager.updatePageEnhancedImage(
                                documentId: documentId,
                                pageId: page.id,
                                newImage: compositeImage
                            )
                            isPlacingSignature = false
                            signatureToPlace = nil
                        },
                        onCancel: {
                            isPlacingSignature = false
                            signatureToPlace = nil
                        }
                    )
                }
            } else {
                VStack {
                    Text("Document not found or empty.")
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle(document?.name ?? "Document")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: exportAndSharePDF) {
                    Image(systemName: "square.and.arrow.up")
                        .foregroundColor(emeraldColor)
                }
            }
        }
        .sheet(isPresented: $showingAddPageScanner) {
            DocumentScannerView(
                isPresented: $showingAddPageScanner,
                onScanFinished: { newImages in
                    storageManager.appendPages(to: documentId, images: newImages)
                    if let doc = document {
                        currentPageIndex = doc.pages.count - 1
                    }
                }
            )
        }
        .sheet(isPresented: $showingSignatureDialog) {
            SignatureDialogView { selectedSig in
                self.signatureToPlace = selectedSig
                self.isPlacingSignature = true
            }
        }
        .sheet(isPresented: Binding(
            get: { activeShareURL != nil },
            set: { if !$0 { activeShareURL = nil } }
        )) {
            if let shareURL = activeShareURL {
                ShareSheet(activityItems: [shareURL])
            }
        }
        .alert("Delete Page", isPresented: $showingDeleteAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                if let page = currentPage {
                    storageManager.deletePage(documentId: documentId, pageId: page.id)
                    if currentPageIndex > 0 {
                        currentPageIndex -= 1
                    }
                    if document?.pages.isEmpty ?? true {
                        dismiss()
                    }
                }
            }
        } message: {
            Text("Are you sure you want to delete this page?")
        }
    }

    // MARK: - Filter Bar

    private func filterSelectionBar(for page: ScannedPage) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(FilterType.allCases) { filter in
                    let isSelected = page.filterType == filter

                    Button(action: {
                        storageManager.updatePageFilter(
                            documentId: documentId,
                            pageId: page.id,
                            filter: filter
                        )
                    }) {
                        HStack(spacing: 4) {
                            if filter == .magicColor {
                                Image(systemName: "sparkles")
                                    .font(.caption)
                            }
                            Text(filter.displayName)
                                .font(.footnote)
                                .fontWeight(isSelected ? .bold : .medium)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(isSelected ? emeraldColor : Color(uiColor: .secondarySystemBackground))
                        .foregroundColor(isSelected ? .white : .primary)
                        .cornerRadius(20)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Action Toolbar

    private var bottomActionToolbar: some View {
        HStack {
            // Add Page
            Button(action: { showingAddPageScanner = true }) {
                VStack(spacing: 4) {
                    Image(systemName: "doc.badge.plus")
                        .font(.system(size: 20))
                    Text("Add Page")
                        .font(.caption2)
                }
                .frame(maxWidth: .infinity)
                .foregroundColor(.primary)
            }

            // Digital Signature
            Button(action: { showingSignatureDialog = true }) {
                VStack(spacing: 4) {
                    Image(systemName: "signature")
                        .font(.system(size: 20))
                    Text("Signature")
                        .font(.caption2)
                }
                .frame(maxWidth: .infinity)
                .foregroundColor(emeraldColor)
            }

            // Share PDF
            Button(action: exportAndSharePDF) {
                VStack(spacing: 4) {
                    Image(systemName: "arrow.down.doc")
                        .font(.system(size: 20))
                    Text("Share PDF")
                        .font(.caption2)
                }
                .frame(maxWidth: .infinity)
                .foregroundColor(.primary)
            }

            // Delete Page
            Button(action: { showingDeleteAlert = true }) {
                VStack(spacing: 4) {
                    Image(systemName: "trash")
                        .font(.system(size: 20))
                    Text("Delete")
                        .font(.caption2)
                }
                .frame(maxWidth: .infinity)
                .foregroundColor(.red)
            }
        }
        .padding(.vertical, 10)
        .background(Color(uiColor: .systemBackground))
        .shadow(color: Color.black.opacity(0.05), radius: 4, x: 0, y: -2)
    }

    private func exportAndSharePDF() {
        guard let doc = document else { return }
        var images: [UIImage] = []
        for p in doc.pages {
            if let img = storageManager.loadImage(named: p.enhancedFileName) {
                images.append(img)
            }
        }
        if let pdfURL = PDFManager.shared.generatePDF(from: images, title: doc.name) {
            self.activeShareURL = pdfURL
        }
    }
}
