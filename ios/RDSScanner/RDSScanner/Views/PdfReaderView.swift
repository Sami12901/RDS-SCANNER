import SwiftUI
import PDFKit

/// Adobe Acrobat PDF Reader & Editor View for iOS.
public struct PdfReaderView: View {
    public let pdfURL: URL
    public let title: String?

    @Environment(\.dismiss) private var dismiss
    @State private var isNightMode: Bool = false
    @State private var showShareSheet = false
    @State private var activeTool: AcrobatTool = .view
    @State private var pdfDoc: PDFDocument?

    public enum AcrobatTool: String, CaseIterable {
        case view = "Pan"
        case highlight = "Highlight"
        case pen = "Pen"
        case text = "Text"
    }

    public init(pdfURL: URL, title: String? = nil) {
        self.pdfURL = pdfURL
        self.title = title
    }

    public var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                PDFKitRepresentedView(url: pdfURL, isNightMode: isNightMode)
                    .background(isNightMode ? Color.black : Color(uiColor: .systemGroupedBackground))
                    .ignoresSafeArea(edges: .bottom)

                // Acrobat Floating Dock
                HStack(spacing: 24) {
                    ForEach(AcrobatTool.allCases, id: \.self) { tool in
                        Button(action: {
                            activeTool = tool
                        }) {
                            VStack(spacing: 4) {
                                Image(systemName: iconFor(tool: tool))
                                    .font(.system(size: 20, weight: .semibold))
                                Text(tool.rawValue)
                                    .font(.system(size: 11, weight: .medium))
                            }
                            .foregroundColor(activeTool == tool ? Color(red: 0.0, green: 0.66, blue: 0.42) : .secondary)
                        }
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 12)
                .background(
                    Capsule()
                        .fill(isNightMode ? Color(white: 0.2) : Color(uiColor: .secondarySystemGroupedBackground))
                        .shadow(color: .black.opacity(0.15), radius: 10, y: 5)
                )
                .padding(.bottom, 16)
            }
            .navigationTitle(title ?? pdfURL.lastPathComponent)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") {
                        dismiss()
                    }
                }

                ToolbarItemGroup(placement: .topBarTrailing) {
                    // Dark / Night Reading Mode Toggle
                    Button(action: { isNightMode.toggle() }) {
                        Image(systemName: isNightMode ? "sun.max.fill" : "moon.fill")
                            .foregroundColor(isNightMode ? .yellow : .primary)
                    }

                    // Share PDF
                    Button(action: { showShareSheet = true }) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
            }
            .sheet(isPresented: $showShareSheet) {
                ActivityView(activityItems: [pdfURL])
            }
        }
    }

    private func iconFor(tool: AcrobatTool) -> String {
        switch tool {
        case .view: return "hand.draw"
        case .highlight: return "highlighter"
        case .pen: return "pencil.tip"
        case .text: return "character.cursor.ibeam"
        }
    }
}

/// UIViewRepresentable wrapping PDFKit's PDFView
struct PDFKitRepresentedView: UIViewRepresentable {
    let url: URL
    let isNightMode: Bool

    func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.document = PDFDocument(url: url)
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.usePageViewController(false)
        updateAppearance(pdfView: pdfView)
        return pdfView
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        updateAppearance(pdfView: uiView)
    }

    private func updateAppearance(pdfView: PDFView) {
        if isNightMode {
            pdfView.backgroundColor = .black
        } else {
            pdfView.backgroundColor = .systemGroupedBackground
        }
    }
}

/// Standard UIActivityViewController wrapper
struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
