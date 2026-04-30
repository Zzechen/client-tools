import Foundation

public struct ViewNode: Codable {
    public let id: String
    public let type: String
    public let screenX: Float
    public let screenY: Float
    public let widthDp: Float
    public let heightDp: Float
    public let visibility: Int
    public let isEnabled: Bool
    public let attrs: NodeAttrs?
    public let translateX: Float
    public let translateY: Float
    public let scaleX: Float
    public let scaleY: Float

    public init(id: String, type: String, screenX: Float, screenY: Float,
                widthDp: Float, heightDp: Float, visibility: Int, isEnabled: Bool,
                attrs: NodeAttrs?,
                translateX: Float = 0, translateY: Float = 0,
                scaleX: Float = 1, scaleY: Float = 1) {
        self.id = id
        self.type = type
        self.screenX = screenX
        self.screenY = screenY
        self.widthDp = widthDp
        self.heightDp = heightDp
        self.visibility = visibility
        self.isEnabled = isEnabled
        self.attrs = attrs
        self.translateX = translateX
        self.translateY = translateY
        self.scaleX = scaleX
        self.scaleY = scaleY
    }
}

public enum NodeAttrs: Codable {
    case text(TextAttrs)
    case image(ImageAttrs)
    case list(ListAttrs)

    private enum CodingKeys: String, CodingKey {
        case type
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "text":  self = .text(try TextAttrs(from: decoder))
        case "image": self = .image(try ImageAttrs(from: decoder))
        case "list":  self = .list(try ListAttrs(from: decoder))
        default: throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "Unknown type")
        }
    }

    public func encode(to encoder: Encoder) throws {
        switch self {
        case .text(let attrs):  try attrs.encode(to: encoder)
        case .image(let attrs): try attrs.encode(to: encoder)
        case .list(let attrs):  try attrs.encode(to: encoder)
        }
    }
}

public struct TextAttrs: Codable {
    public let type: String
    public let fontSize: Float?
    public let color: String?
    public let fontWeight: String?

    public init(fontSize: Float? = nil, color: String? = nil, fontWeight: String? = nil) {
        self.type = "text"
        self.fontSize = fontSize
        self.color = color
        self.fontWeight = fontWeight
    }

    enum CodingKeys: String, CodingKey {
        case type, fontSize, color, fontWeight
    }
}

public struct ImageAttrs: Codable {
    public let type: String
    public let scaleType: String?

    public init(scaleType: String? = nil) {
        self.type = "image"
        self.scaleType = scaleType
    }

    enum CodingKeys: String, CodingKey {
        case type, scaleType
    }
}

public struct ListAttrs: Codable {
    public let type: String
    public let itemSpacing: Float?
    public let orientation: String?

    public init(itemSpacing: Float? = nil, orientation: String? = nil) {
        self.type = "list"
        self.itemSpacing = itemSpacing
        self.orientation = orientation
    }

    enum CodingKeys: String, CodingKey {
        case type, itemSpacing, orientation
    }
}
