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
        let cls = type(of: self)
        let name = String(describing: cls)
        // 跳过系统 VC：UIKit 内部类名以 UI/NS/_UI 开头，或非 app bundle 中的类
        guard !name.hasPrefix("UI"),
              !name.hasPrefix("NS"),
              !name.hasPrefix("_"),
              Bundle(for: cls) == Bundle.main else { return }
        ClientToolsSDK.shared.recordPageChange(name)
    }
}
