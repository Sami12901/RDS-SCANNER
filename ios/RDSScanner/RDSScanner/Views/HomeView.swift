import SwiftUI

/// Main dashboard for RDS SCANNER on iOS
public struct HomeView: View {
    @StateObject private var storageManager = DocumentStorageManager.shared
    @State private var showingScanner = false
    @State private var showingAbout = false
    @State private var searchText = ""
    @State private var navigationPath = NavigationPath()
    @State private var documentToRename: ScannedDocument?
    @State private var newDocumentName = ""
    @State private var showingRenameDialog = false
    @State private var activeShareURL: URL?

    // Emerald brand theme
    private let emeraldColor = Color(red: 0.0, green: 0.66, blue: 0.42)

    private var filteredDocuments: [ScannedDocument] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return storageManager.documents
        } else {
            return storageManager.documents.filter {
                $0.name.localizedCaseInsensitiveContains(searchText)
            }
        }
    }

    public init() {}

    public var body: some View {
        NavigationStack(path: $navigationPath) {
            ZStack(alignment: .bottomTrailing) {
                VStack(spacing: 0) {
                    // Header with Sami12901 branding
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("RDS SCANNER")
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundColor(.primary)

                            Link(destination: URL(string: "https://github.com/Sami12901")!) {
                                HStack(spacing: 4) {
                                    Text("by Sami12901")
                                        .font(.caption)
                                        .foregroundColor(emeraldColor)
                                        .fontWeight(.medium)
                                    Image(systemName: "arrow.up.right.square")
                                        .font(.caption2)
                                        .foregroundColor(emeraldColor)
                                }
                            }
                        }

                        Spacer()

                        Button(action: { showingAbout = true }) {
                            Image(systemName: "info.circle")
                                .font(.title3)
                                .foregroundColor(emeraldColor)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .padding(.bottom, 6)

                    // Search Bar
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.secondary)
                        TextField("Search documents...", text: $searchText)
                        if !searchText.isEmpty {
                            Button(action: { searchText = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(10)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal)
                    .padding(.vertical, 6)

                    // Document List or Empty State
                    if filteredDocuments.isEmpty {
                        emptyStateView
                    } else {
                        documentListView
                    }
                }

                // Emerald Floating Action Button (Scan Document)
                Button(action: { showingScanner = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "camera.viewfinder")
                            .font(.title3)
                        Text("Scan Document")
                            .fontWeight(.semibold)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .background(emeraldColor)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
                    .shadow(color: emeraldColor.opacity(0.4), radius: 8, x: 0, y: 4)
                }
                .padding(.trailing, 20)
                .padding(.bottom, 24)
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingScanner) {
                DocumentScannerView(
                    isPresented: $showingScanner,
                    onScanFinished: { scannedImages in
                        guard !scannedImages.isEmpty else { return }
                        let formatter = DateFormatter()
                        formatter.dateFormat = "yyyy-MM-dd_HHmmss"
                        let docName = "Scan_\(formatter.string(from: Date()))"
                        let newDoc = storageManager.createDocument(name: docName, scannedImages: scannedImages)
                        navigationPath.append(newDoc)
                    }
                )
            }
            .sheet(isPresented: $showingAbout) {
                AboutView()
            }
            .navigationDestination(for: ScannedDocument.self) { doc in
                DocumentDetailView(documentId: doc.id)
            }
            .alert("Rename Document", isPresented: $showingRenameDialog) {
                TextField("Document Name", text: $newDocumentName)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    if let doc = documentToRename, !newDocumentName.isEmpty {
                        storageManager.renameDocument(id: doc.id, newName: newDocumentName)
                    }
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
        }
    }

    // MARK: - Views

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "doc.viewfinder")
                .font(.system(size: 64))
                .foregroundColor(emeraldColor.opacity(0.7))

            Text("No Documents Yet")
                .font(.title3)
                .fontWeight(.bold)

            Text("Tap 'Scan Document' to capture high-quality documents with automatic edge detection, CamScanner Magic Color, and zero watermarks.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Spacer()
            Spacer()
        }
    }

    private var documentListView: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(filteredDocuments) { doc in
                    DocumentCard(
                        document: doc,
                        onTap: {
                            navigationPath.append(doc)
                        },
                        onShare: {
                            shareDocumentAsPDF(doc)
                        },
                        onRename: {
                            documentToRename = doc
                            newDocumentName = doc.name
                            showingRenameDialog = true
                        },
                        onDelete: {
                            storageManager.deleteDocument(id: doc.id)
                        }
                    )
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)
            .padding(.bottom, 90) // clearance for FAB
        }
    }

    private func shareDocumentAsPDF(_ doc: ScannedDocument) {
        var images: [UIImage] = []
        for page in doc.pages {
            if let img = storageManager.loadImage(named: page.enhancedFileName) {
                images.append(img)
            }
        }

        if let pdfURL = PDFManager.shared.generatePDF(from: images, title: doc.name) {
            self.activeShareURL = pdfURL
        }
    }
}

// MARK: - Document Card View

struct DocumentCard: View {
    let document: ScannedDocument
    let onTap: () -> Void
    let onShare: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void

    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: document.updatedAt)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                // Thumbnail preview
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(uiColor: .tertiarySystemBackground))
                        .frame(width: 64, height: 80)

                    if let first = document.firstPage,
                       let thumb = DocumentStorageManager.shared.loadImage(named: first.enhancedFileName) {
                        Image(uiImage: thumb)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 64, height: 80)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        Image(systemName: "doc.text")
                            .font(.title2)
                            .foregroundColor(.secondary)
                    }
                }
                .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)

                // Info
                VStack(alignment: .leading, spacing: 6) {
                    Text(document.name)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    HStack(spacing: 8) {
                        Label("\(document.pageCount) \(document.pageCount == 1 ? "page" : "pages")", systemImage: "doc.on.doc")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Text("•")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Text(formattedDate)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                // Context Menu
                Menu {
                    Button(action: onShare) {
                        Label("Share PDF", systemImage: "square.and.arrow.up")
                    }
                    Button(action: onRename) {
                        Label("Rename", systemImage: "pencil")
                    }
                    Divider()
                    Button(role: .destructive, action: onDelete) {
                        Label("Delete", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .padding(10)
                }
            }
            .padding(12)
            .background(Color(uiColor: .secondarySystemBackground))
            .cornerRadius(14)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Native iOS UIActivityViewController Wrapper

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
