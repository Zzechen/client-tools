import UIKit

class ViewControllerSwizzling {

    private static var isSwizzled = false

    static func swizzle() {
        guard !isSwizzled else { return }
        isSwizzled = true

        let originalSelector = #selector(UIViewController.viewDidAppear(_:))
        let swizzledSelector = #selector(UIViewController.ct_viewDidAppear(_:))

        guard let originalMethod = class_getInstanceMethod(UIViewController.self, originalSelector),
              let swizzledMethod = class_getInstanceMethod(UIViewController.self, swizzledSelector) else {
            return
        }
        method_exchangeImplementations(originalMethod, swizzledMethod)
    }
}

extension UIViewController {
    @objc func ct_viewDidAppear(_ animated: Bool) {
        self.ct_viewDidAppear(animated)
        let className = String(describing: type(of: self))
        ClientToolsSDK.shared.recordPageChange(className)
    }
}
