import SwiftUI

/// An interactive overlay placed on top of a scanned document page, allowing the user
/// to drag, scale, recolor, and burn a signature directly into the page image.
@MainActor
public struct SignatureOverlayView: View {
    public let baseImage: UIImage
    @Binding public var signatureImage: UIImage?
    public var onApply: (UIImage) -> Void
    public var onCancel: () -> Void

    // Interactive placement state
    @State private var positionOffset: CGSize = .zero
    @State private var dragAccumulator: CGSize = .zero
    @State private var signatureScale: CGFloat = 0.35
    @State private var selectedInkColor: UIColor = .black

    // Geometry measurements
    @State private var containerSize: CGSize = .zero

    private let inkColors: [(name: String, color: Color, uiColor: UIColor)] = [
        ("Black", Color(red: 0.12, green: 0.12, blue: 0.12), UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1.0)),
        ("Blue", Color(red: 0.12, green: 0.25, blue: 0.69), UIColor(red: 0.12, green: 0.25, blue: 0.69, alpha: 1.0)),
        ("Green", Color(red: 0.0, green: 0.66, blue: 0.42), UIColor(red: 0.0, green: 0.66, blue: 0.42, alpha: 1.0)),
        ("Red", Color(red: 0.86, green: 0.15, blue: 0.15), UIColor(red: 0.86, green: 0.15, blue: 0.15, alpha: 1.0))
    ]

    public var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                // Background overlay detection
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                // Active Document Preview with Signature Drag Target
                VStack {
                    ZStack {
                        Image(uiImage: baseImage)
                            .resizable()
                            .scaledToFit()
                            .overlay(
                                GeometryReader { imageGeo in
                                    Color.clear
                                        .onAppear {
                                            self.containerSize = imageGeo.size
                                        }
                                        .onChange(of: imageGeo.size) { newSize in
                                            self.containerSize = newSize
                                        }
                                }
                            )

                        if let rawSig = signatureImage {
                            let recoloredSig = SignatureManager.shared.recolor(image: rawSig, to: selectedInkColor)
                            let sigWidth = containerSize.width * signatureScale

                            VStack(spacing: 0) {
                                Image(uiImage: recoloredSig)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: max(60, sigWidth))
                                    .padding(4)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 4)
                                            .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                                            .foregroundColor(.blue)
                                    )
                            }
                            .offset(x: positionOffset.width + dragAccumulator.width,
                                    y: positionOffset.height + dragAccumulator.height)
                            .gesture(
                                DragGesture()
                                    .onChanged { value in
                                        dragAccumulator = value.translation
                                    }
                                    .onEnded { value in
                                        positionOffset.width += value.translation.width
                                        positionOffset.height += value.translation.height
                                        dragAccumulator = .zero
                                    }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                    Spacer()
                }

                // Bottom Control Sheet
                VStack(spacing: 12) {
                    // Size slider
                    HStack(spacing: 12) {
                        Image(systemName: "textformat.size.smaller")
                            .foregroundColor(.secondary)
                        Slider(value: $signatureScale, in: 0.15...0.75)
                            .tint(Color(red: 0.0, green: 0.66, blue: 0.42))
                        Image(systemName: "textformat.size.larger")
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal)

                    // Color palette
                    HStack(spacing: 16) {
                        Text("Ink Color")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        ForEach(inkColors, id: \.name) { item in
                            Circle()
                                .fill(item.color)
                                .frame(width: 28, height: 28)
                                .overlay(
                                    Circle()
                                        .stroke(Color.primary, lineWidth: selectedInkColor == item.uiColor ? 2 : 0)
                                        .padding(-2)
                                )
                                .onTapGesture {
                                    selectedInkColor = item.uiColor
                                }
                        }

                        Spacer()
                    }
                    .padding(.horizontal)

                    // Action buttons
                    HStack(spacing: 12) {
                        Button(action: onCancel) {
                            Text("Cancel")
                                .fontWeight(.medium)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(Color(uiColor: .secondarySystemBackground))
                                .foregroundColor(.primary)
                                .cornerRadius(10)
                        }

                        Button(action: bakeAndApplySignature) {
                            HStack {
                                Image(systemName: "checkmark")
                                Text("Apply Signature")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color(red: 0.0, green: 0.66, blue: 0.42))
                            .foregroundColor(.white)
                            .cornerRadius(10)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 12)
                }
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(uiColor: .systemBackground))
                        .shadow(radius: 12)
                )
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            }
        }
    }

    // MARK: - Burning the Signature into Page Bitmap

    private func bakeAndApplySignature() {
        guard let rawSig = signatureImage, containerSize.width > 0, containerSize.height > 0 else {
            onCancel()
            return
        }

        let recoloredSig = SignatureManager.shared.recolor(image: rawSig, to: selectedInkColor)

        // Calculate scaling ratio from UI container back to full-resolution base image
        let scaleX = baseImage.size.width / containerSize.width
        let scaleY = baseImage.size.height / containerSize.height

        let targetSigWidth = (containerSize.width * signatureScale) * scaleX
        let targetSigHeight = (targetSigWidth / recoloredSig.size.width) * recoloredSig.size.height

        // Calculate center position on base image
        let centerX = (containerSize.width / 2 + positionOffset.width) * scaleX
        let centerY = (containerSize.height / 2 + positionOffset.height) * scaleY

        let destRect = CGRect(
            x: centerX - targetSigWidth / 2,
            y: centerY - targetSigHeight / 2,
            width: targetSigWidth,
            height: targetSigHeight
        )

        let renderer = UIGraphicsImageRenderer(size: baseImage.size)
        let compositeImage = renderer.image { _ in
            baseImage.draw(in: CGRect(origin: .zero, size: baseImage.size))
            recoloredSig.draw(in: destRect)
        }

        onApply(compositeImage)
    }
}
