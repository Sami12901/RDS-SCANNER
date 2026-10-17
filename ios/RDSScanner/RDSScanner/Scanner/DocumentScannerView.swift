import SwiftUI
import VisionKit

/// Wraps Apple's first-party VisionKit VNDocumentCameraViewController in SwiftUI.
/// Provides identical scanning experience to Apple Notes and CamScanner iOS:
/// - Real-time page boundary quadrilateral detection
/// - Automated perspective warp & de-skew
/// - Multi-page batch document capture
/// - Torch / Flashlight controls
public struct DocumentScannerView: UIViewControllerRepresentable {
    public typealias UIViewControllerType = VNDocumentCameraViewController

    @Binding public var isPresented: Bool
    public var onScanFinished: ([UIImage]) -> Void
    public var onScanCancelled: (() -> Void)?

    public init(
        isPresented: Binding<Bool>,
        onScanFinished: @escaping ([UIImage]) -> Void,
        onScanCancelled: (() -> Void)? = nil
    ) {
        self._isPresented = isPresented
        self.onScanFinished = onScanFinished
        self.onScanCancelled = onScanCancelled
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scannerViewController = VNDocumentCameraViewController()
        scannerViewController.delegate = context.coordinator
        return scannerViewController
    }

    public func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {
        // No-op
    }

    public static var isSupported: Bool {
        VNDocumentCameraViewController.isSupported
    }

    public final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        private let parent: DocumentScannerView

        init(_ parent: DocumentScannerView) {
            self.parent = parent
        }

        public func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFinishWith scan: VNDocumentCameraScan
        ) {
            var scannedImages: [UIImage] = []
            for pageIndex in 0..<scan.pageCount {
                let image = scan.imageOfPage(at: pageIndex)
                scannedImages.append(image)
            }

            parent.isPresented = false
            parent.onScanFinished(scannedImages)
        }

        public func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            parent.isPresented = false
            parent.onScanCancelled?()
        }

        public func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFailWithError error: Error
        ) {
            print("VisionKit Document Scanner error: \(error.localizedDescription)")
            parent.isPresented = false
            parent.onScanCancelled?()
        }
    }
}
