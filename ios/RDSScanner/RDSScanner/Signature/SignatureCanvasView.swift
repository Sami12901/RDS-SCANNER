import SwiftUI
import PencilKit

/// Interactive PencilKit Canvas wrapper supporting Apple Pencil and finger touch
public struct PencilKitCanvas: UIViewRepresentable {
    @Binding public var canvasView: PKCanvasView
    public var inkColor: UIColor
    public var inkWidth: CGFloat

    public func makeUIView(context: Context) -> PKCanvasView {
        canvasView.tool = PKInkingTool(.pen, color: inkColor, width: inkWidth)
        canvasView.drawingPolicy = .anyInput
        canvasView.backgroundColor = .clear
        canvasView.isOpaque = false
        return canvasView
    }

    public func updateUIView(_ uiView: PKCanvasView, context: Context) {
        uiView.tool = PKInkingTool(.pen, color: inkColor, width: inkWidth)
    }
}

/// Full modal dialog for drawing or choosing a saved digital signature
public struct SignatureDialogView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var signatureManager = SignatureManager.shared

    @State private var selectedTab = 0
    @State private var canvasView = PKCanvasView()
    @State private var selectedColor: Color = Color(red: 0.12, green: 0.12, blue: 0.12)
    @State private var strokeWidth: CGFloat = 5.0

    public var onSignatureSelected: (UIImage) -> Void

    private let availableColors: [(name: String, color: Color, uiColor: UIColor)] = [
        ("Black", Color(red: 0.12, green: 0.12, blue: 0.12), UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1.0)),
        ("Royal Blue", Color(red: 0.12, green: 0.25, blue: 0.69), UIColor(red: 0.12, green: 0.25, blue: 0.69, alpha: 1.0)),
        ("Emerald", Color(red: 0.0, green: 0.66, blue: 0.42), UIColor(red: 0.0, green: 0.66, blue: 0.42, alpha: 1.0)),
        ("Ruby Red", Color(red: 0.86, green: 0.15, blue: 0.15), UIColor(red: 0.86, green: 0.15, blue: 0.15, alpha: 1.0))
    ]

    public init(onSignatureSelected: @escaping (UIImage) -> Void) {
        self.onSignatureSelected = onSignatureSelected
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab Selector
                Picker("Mode", selection: $selectedTab) {
                    Text("Draw Signature").tag(0)
                    Text("Saved Library (\(signatureManager.savedSignatureFiles.count))").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()

                if selectedTab == 0 {
                    drawSignatureView
                } else {
                    savedSignaturesLibraryView
                }
            }
            .navigationTitle("Digital Signature")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }

    // MARK: - Draw Signature Mode

    private var drawSignatureView: some View {
        VStack(spacing: 16) {
            // Drawing Canvas Card
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(uiColor: .systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 4)

                // Grid background guidelines
                VStack(spacing: 24) {
                    Divider().opacity(0.4)
                    Divider().opacity(0.4)
                    Divider().opacity(0.4)
                }
                .padding(.horizontal, 16)

                let currentUIColor = availableColors.first(where: { $0.color == selectedColor })?.uiColor ?? .black
                PencilKitCanvas(canvasView: $canvasView, inkColor: currentUIColor, inkWidth: strokeWidth)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                // Bottom canvas hint
                Text("Sign with Finger or Apple Pencil")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(10)
            }
            .frame(height: 240)
            .padding(.horizontal)

            // Color Swatches
            HStack(spacing: 16) {
                Text("Ink Color:")
                    .font(.subheadline)
                    .fontWeight(.medium)

                ForEach(availableColors, id: \.name) { item in
                    Circle()
                        .fill(item.color)
                        .frame(width: 32, height: 32)
                        .overlay(
                            Circle()
                                .stroke(Color.primary, lineWidth: selectedColor == item.color ? 3 : 0)
                                .padding(-3)
                        )
                        .onTapGesture {
                            selectedColor = item.color
                        }
                }

                Spacer()

                // Clear button
                Button(action: {
                    canvasView.drawing = PKDrawing()
                }) {
                    Label("Clear", systemImage: "arrow.counterclockwise")
                        .font(.footnote)
                        .foregroundColor(.red)
                }
            }
            .padding(.horizontal)

            Spacer()

            // Confirm & Save
            Button(action: saveAndApplyDrawnSignature) {
                HStack {
                    Image(systemName: "checkmark")
                    Text("Save & Use Signature")
                        .fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color(red: 0.0, green: 0.66, blue: 0.42))
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            .padding(.horizontal)
            .padding(.bottom, 16)
        }
    }

    private func saveAndApplyDrawnSignature() {
        let drawing = canvasView.drawing
        guard !drawing.bounds.isEmpty else { return }

        // Render transparent image from drawing
        let image = drawing.image(from: drawing.bounds, scale: 2.0)

        // Save to signature library and auto-crop
        if let savedImage = signatureManager.saveSignature(image) {
            dismiss()
            onSignatureSelected(savedImage)
        }
    }

    // MARK: - Saved Signatures Library Mode

    private var savedSignaturesLibraryView: some View {
        Group {
            if signatureManager.savedSignatureFiles.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "signature")
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                    Text("No Saved Signatures Yet")
                        .font(.headline)
                    Text("Draw and save a signature to reuse it across any scanned document.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                        ForEach(signatureManager.savedSignatureFiles, id: \.self) { fileName in
                            if let signatureImage = signatureManager.loadSignature(named: fileName) {
                                VStack {
                                    ZStack(alignment: .topTrailing) {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color(uiColor: .secondarySystemBackground))
                                            .frame(height: 110)

                                        Image(uiImage: signatureImage)
                                            .resizable()
                                            .scaledToFit()
                                            .frame(height: 80)
                                            .padding(12)

                                        Button(action: {
                                            signatureManager.deleteSignature(named: fileName)
                                        }) {
                                            Image(systemName: "trash.circle.fill")
                                                .font(.title3)
                                                .foregroundColor(.red)
                                                .padding(6)
                                        }
                                    }

                                    Button("Use This") {
                                        dismiss()
                                        onSignatureSelected(signatureImage)
                                    }
                                    .font(.footnote)
                                    .fontWeight(.medium)
                                    .foregroundColor(Color(red: 0.0, green: 0.66, blue: 0.42))
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
        }
    }
}
