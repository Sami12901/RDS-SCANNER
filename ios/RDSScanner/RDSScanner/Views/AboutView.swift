import SwiftUI

/// About view for RDS SCANNER iOS highlighting 100% on-device privacy
/// and open-source GitHub attribution to Sami12901.
public struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    private let emeraldColor = Color(red: 0.0, green: 0.66, blue: 0.42)

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // App Logo & Title
                    VStack(spacing: 8) {
                        Image(systemName: "doc.viewfinder.fill")
                            .font(.system(size: 64))
                            .foregroundColor(emeraldColor)
                            .padding(.top, 16)

                        Text("RDS SCANNER")
                            .font(.title)
                            .fontWeight(.bold)

                        Text("Version 1.3.0 (iOS Native)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    // Value Badges
                    HStack(spacing: 12) {
                        BadgeView(icon: "lock.shield", title: "100% Private", subtitle: "On-Device")
                        BadgeView(icon: "bolt.fill", title: "Metal GPU", subtitle: "Real-time")
                        BadgeView(icon: "checkmark.seal", title: "No Watermark", subtitle: "Clean PDF")
                    }
                    .padding(.horizontal)

                    // Description Card
                    VStack(alignment: .leading, spacing: 12) {
                        Text("About RDS SCANNER")
                            .font(.headline)

                        Text("A free, privacy-first, open-source CamScanner alternative for iPhone. Everything happens 100% on your device with Apple's VisionKit, CoreImage Metal GPU filters, and PencilKit signatures.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineSpacing(4)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(16)
                    .padding(.horizontal)

                    // Developer & GitHub Section
                    VStack(spacing: 16) {
                        // Author card
                        Link(destination: URL(string: "https://github.com/Sami12901")!) {
                            HStack(spacing: 14) {
                                Image(systemName: "person.crop.circle.fill")
                                    .font(.system(size: 36))
                                    .foregroundColor(emeraldColor)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Developed by Sami")
                                        .font(.headline)
                                        .foregroundColor(.primary)
                                    Text("@Sami12901 on GitHub")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                Image(systemName: "arrow.up.right")
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                            }
                            .padding()
                            .background(Color(uiColor: .secondarySystemBackground))
                            .cornerRadius(14)
                        }

                        // Repository Star Button
                        Link(destination: URL(string: "https://github.com/Sami12901/RDS-SCANNER")!) {
                            HStack {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.yellow)
                                Text("Star on GitHub (RDS-SCANNER)")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(emeraldColor)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 20)
                }
            }
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct BadgeView: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(Color(red: 0.0, green: 0.66, blue: 0.42))
            Text(title)
                .font(.caption)
                .fontWeight(.bold)
            Text(subtitle)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color(uiColor: .secondarySystemBackground))
        .cornerRadius(12)
    }
}
