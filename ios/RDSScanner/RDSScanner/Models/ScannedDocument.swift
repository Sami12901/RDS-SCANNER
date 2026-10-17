import Foundation
import SwiftUI

/// Represents the image enhancement filter applied to a scanned page
public enum FilterType: String, Codable, CaseIterable, Identifiable {
    case original = "ORIGINAL"
    case magicColor = "MAGIC_COLOR"
    case blackAndWhite = "BLACK_WHITE"
    case grayscale = "GRAYSCALE"
    case lighten = "LIGHTEN"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .original: return "Original"
        case .magicColor: return "Magic Color"
        case .blackAndWhite: return "B & W"
        case .grayscale: return "Grayscale"
        case .lighten: return "Lighten"
        }
    }
}

/// A single page inside a scanned document
public struct ScannedPage: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var pageIndex: Int
    /// File name of the pristine raw camera capture (used for non-destructive filter switching)
    public var originalFileName: String
    /// File name of the currently active enhanced/filtered page image
    public var enhancedFileName: String
    public var filterType: FilterType
    public var rotationDegrees: Int
    public var dateAdded: Date

    public init(
        id: String = UUID().uuidString,
        pageIndex: Int,
        originalFileName: String,
        enhancedFileName: String,
        filterType: FilterType = .magicColor,
        rotationDegrees: Int = 0,
        dateAdded: Date = Date()
    ) {
        self.id = id
        self.pageIndex = pageIndex
        self.originalFileName = originalFileName
        self.enhancedFileName = enhancedFileName
        self.filterType = filterType
        self.rotationDegrees = rotationDegrees
        self.dateAdded = dateAdded
    }
}

/// A complete multi-page scanned document
public struct ScannedDocument: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var pages: [ScannedPage]
    public var createdAt: Date
    public var updatedAt: Date

    public init(
        id: String = UUID().uuidString,
        name: String,
        pages: [ScannedPage] = [],
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.pages = pages
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public var pageCount: Int {
        pages.count
    }

    public var firstPage: ScannedPage? {
        pages.first
    }
}
