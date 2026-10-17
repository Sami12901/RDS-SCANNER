import SwiftUI

@main
public struct RDSScannerApp: App {
    // Brand emerald accent color
    private let emeraldColor = Color(red: 0.0, green: 0.66, blue: 0.42)

    public init() {
        // Global appearance customization
        let appearance = UINavigationBarAppearance()
        appearance.configureWithDefaultBackground()
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
    }

    public var body: some Scene {
        WindowGroup {
            MainTabView()
                .tint(emeraldColor)
        }
    }
}
