import SwiftUI
import PDFKit

/// iOS 3-Hub TabView unifying CamScanner and Adobe Acrobat Reader: Edit PDF.
public struct MainTabView: View {
    @State private var selectedTab: Int = 0

    public init() {}

    public var body: some View {
        TabView(selection: $selectedTab) {
            // Hub 1: CamScanner Document Scanner & Library
            HomeView()
                .tabItem {
                    Label("Scanner & Docs", systemImage: "doc.viewfinder.fill")
                }
                .tag(0)

            // Hub 2: Adobe Acrobat PDF Tools Suite
            PdfToolsView()
                .tabItem {
                    Label("PDF Tools", systemImage: "wrench.and.screwdriver.fill")
                }
                .tag(1)

            // Hub 3: Adobe Acrobat PDF Reader
            PdfReaderHubView()
                .tabItem {
                    Label("PDF Reader", systemImage: "book.pages.fill")
                }
                .tag(2)
        }
    }
}

/// Hub 3: PDF Reader Launcher & Recent Documents
struct PdfReaderHubView: View {
    @ObservedObject private var storage = DocumentStorageManager.shared
    @State private var showPicker = false
    @State private var selectedPdfURL: URL?
    @State private var showReader = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Open PDF from Files Action Card
                    Button(action: { showPicker = true }) {
                        HStack(spacing: 16) {
                            Image(systemName: "folder.fill")
                                .font(.system(size: 28))
                                .foregroundColor(.white)
                                .frame(width: 56, height: 56)
                                .background(Color(red: 0.0, green: 0.66, blue: 0.42))
                                .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Open PDF File")
                                    .font(.headline)
                                    .fontWeight(.bold)
                                    .foregroundColor(.primary)
                                Text("Browse files from your iPhone, iCloud Drive, or Downloads")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                        .padding()
                        .background(Color(red: 0.0, green: 0.66, blue: 0.42).opacity(0.12))
                        .cornerRadius(16)
                    }

                    // Scanned Documents Section
                    HStack {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundColor(Color(red: 0.0, green: 0.66, blue: 0.42))
                        Text("Your Scanned Documents")
                            .font(.headline)
                        Spacer()
                    }
                    .padding(.top, 8)

                    if storage.documents.isEmpty {
                        Text("No scanned documents yet. Use the 'Scanner & Docs' tab or tap 'Open PDF File' to read any PDF.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(Color(uiColor: .secondarySystemGroupedBackground))
                            .cornerRadius(12)
                    } else {
                        ForEach(storage.documents) { doc in
                            Button(action: {
                                let images = doc.pages.compactMap { storage.loadImage(named: $0.enhancedFileName) }
                                if let url = PDFManager.shared.generatePDF(from: images, title: doc.title) {
                                    selectedPdfURL = url
                                    showReader = true
                                }
                            }) {
                                HStack(spacing: 14) {
                                    Image(systemName: "doc.richtext.fill")
                                        .font(.system(size: 24))
                                        .foregroundColor(.red)
                                        .frame(width: 44, height: 44)
                                        .background(Color.red.opacity(0.12))
                                        .clipShape(RoundedRectangle(cornerRadius: 10))

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(doc.title)
                                            .font(.headline)
                                            .foregroundColor(.primary)
                                        Text("\(doc.pages.count) pages")
                                            .font(.footnote)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Text("Read & Edit")
                                        .font(.footnote)
                                        .fontWeight(.bold)
                                        .foregroundColor(Color(red: 0.0, green: 0.66, blue: 0.42))
                                }
                                .padding()
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .cornerRadius(14)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Adobe Acrobat Reader")
            .sheet(isPresented: $showPicker) {
                DocumentPicker(activeTool: "PDF") { url in
                    if let url = url {
                        selectedPdfURL = url
                        showReader = true
                    }
                }
            }
            .sheet(isPresented: $showReader) {
                if let url = selectedPdfURL {
                    PdfReaderView(pdfURL: url, title: url.lastPathComponent)
                }
            }
        }
    }
}
