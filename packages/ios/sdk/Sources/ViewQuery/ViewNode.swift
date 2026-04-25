import Foundation

public struct ViewNode: Codable {
    public let id: String
    public let type: String          // TEXT, IMAGE, LIST, CONTAINER
    public let screenX: Float       // dp
    public let screenY: Float       // dp
    public let widthDp: Float      // dp
    public let heightDp: Float      // dp
    public let attrs: NodeAttrs?    // TextAttrs, ImageAttrs, etc.

    public init(id: String, type: String, screenX: Float, screenY: Float,
                widthDp: Float, heightDp: Float, attrs: NodeAttrs?) {
        self.id = id
        self.type = type
        self.screenX = screenX
        self.screenY = screenY
        self.widthDp = widthDp
        self.heightDp = heightDp
        self.attrs = attrs
    }
}

public enum NodeAttrs: Codable {
    case text(TextAttrs)
    case image(ImageAttrs)
    case list(ListAttrs)
    case none

    private enum CodingKeys: String, CodingKey {
        case type, data
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "text": self = .text(try container.decode(TextAttrs.self, forKey: .data))
        case "image": self = .image(try container.decode(ImageAttrs.self, forKey: .data))
        case "list": self = .list(try container.decode(ListAttrs.self, forKey: .data))
        default: self = .none
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .text(let attrs):
            try container.encode("text", forKey: .type)
            try container.encode(attrs, forKey: .data)
        case .image(let attrs):
            try container.encode("image", forKey: .type)
            try container.encode(attrs, forKey: .data)
        case .list(let attrs):
            try container.encode("list", forKey: .type)
            try container.encode(attrs, forKey: .data)
        case .none:
            try container.encode("none", forKey: .type)
        }
    }
}

public struct TextAttrs: Codable {
    public let fontSize: Float?
    public let color: String?
    public let fontWeight: String?

    public init(fontSize: Float? = nil, color: String? = nil, fontWeight: String? = nil) {
        self.fontSize = fontSize
        self.color = color
        self.fontWeight = fontWeight
    }
}

public struct ImageAttrs: Codable {
    public let scaleType: String?

    public init(scaleType: String? = nil) {
        self.scaleType = scaleType
    }
}

public struct ListAttrs: Codable {
    public let itemSpacing: Float?
    public let orientation: String?

    public init(itemSpacing: Float? = nil, orientation: String? = nil) {
        self.itemSpacing = itemSpacing
        self.orientation = orientation
    }
}
