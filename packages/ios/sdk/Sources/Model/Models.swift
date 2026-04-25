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

    public init(marginTopDiffDp: Float? = nil, marginBottomDiffDp: Float? = nil,
                 marginLeftDiffDp: Float? = nil, marginRightDiffDp: Float? = nil,
                 paddingTopDiffDp: Float? = nil, paddingBottomDiffDp: Float? = nil,
                 paddingLeftDiffDp: Float? = nil, paddingRightDiffDp: Float? = nil,
                 widthDp: String? = nil, heightDp: String? = nil) {
        self.marginTopDiffDp = marginTopDiffDp
        self.marginBottomDiffDp = marginBottomDiffDp
        self.marginLeftDiffDp = marginLeftDiffDp
        self.marginRightDiffDp = marginRightDiffDp
        self.paddingTopDiffDp = paddingTopDiffDp
        self.paddingBottomDiffDp = paddingBottomDiffDp
        self.paddingLeftDiffDp = paddingLeftDiffDp
        self.paddingRightDiffDp = paddingRightDiffDp
        self.widthDp = widthDp
        self.heightDp = heightDp
    }
}

public struct OverlayShowRequest: Codable {
    public let url: String
    public let opacity: Float

    public init(url: String, opacity: Float) {
        self.url = url
        self.opacity = opacity
    }
}

public struct OverlayOpacityRequest: Codable {
    public let opacity: Float

    public init(opacity: Float) {
        self.opacity = opacity
    }
}

public struct PushHtmlRequest: Codable {
    public let tag: String
    public let html: String

    public init(tag: String, html: String) {
        self.tag = tag
        self.html = html
    }
}

public struct PushHtmlResult: Codable {
    public let url: String

    public init(url: String) {
        self.url = url
    }
}
