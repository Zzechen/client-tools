import Foundation

public struct PageInfo: Codable {
    public let pageName: String
    public let timestamp: String

    public init(pageName: String, timestamp: String) {
        self.pageName = pageName
        self.timestamp = timestamp
    }
}

public struct ClickRequest: Codable {
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

public struct ClickResult: Codable {
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

public struct ScrollRequest: Codable {
    public let id: String
    public let dx: Float
    public let dy: Float

    public init(id: String, dx: Float, dy: Float) {
        self.id = id
        self.dx = dx
        self.dy = dy
    }
}

public struct ScrollResult: Codable {
    public let id: String
    public let dx: Float
    public let dy: Float

    public init(id: String, dx: Float, dy: Float) {
        self.id = id
        self.dx = dx
        self.dy = dy
    }
}

public struct ModifyRequest: Codable {
    public let id: String
    public let props: ModifyProps

    public init(id: String, props: ModifyProps) {
        self.id = id
        self.props = props
    }
}

public struct ModifyProps: Codable {
    public let marginTopDiffDp: Float?
    public let marginBottomDiffDp: Float?
    public let marginLeftDiffDp: Float?
    public let marginRightDiffDp: Float?
    public let paddingTopDiffDp: Float?
    public let paddingBottomDiffDp: Float?
    public let paddingLeftDiffDp: Float?
    public let paddingRightDiffDp: Float?
    public let widthDp: String?
    public let heightDp: String?
    public let letterSpacingEm: Float?
    public let lineSpacingExtraDp: Float?
    public let includeFontPadding: Bool?
}

public struct WebviewPushHtmlRequest: Codable {
    public let tag: String
    public let html: String
    public let timestamp: String
}

public struct WebviewShowRequest: Codable {
    public let tag: String
    public let timestamp: String
}

public struct WebviewAdjustRequest: Codable {
    public let offsetX: Float?
    public let offsetY: Float?
    public let opacity: Float?
}
