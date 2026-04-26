import Foundation
import UIKit

class PageTracker {

    private var currentPageName: String = ""
    private var lastChangeTime: String = ""

    func start() {
        ViewControllerSwizzling.swizzle()
    }

    func recordPageChange(_ pageName: String) {
        currentPageName = pageName
        let formatter = DateFormatter()
        formatter.dateFormat = "MMdd-HHmm"
        lastChangeTime = formatter.string(from: Date())
    }

    func getCurrentPage() -> (pageName: String, timestamp: String) {
        return (currentPageName, lastChangeTime)
    }
}
