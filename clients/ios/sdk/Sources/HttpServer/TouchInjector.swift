import UIKit
import Darwin
import ObjectiveC

/// 触摸注入引擎。
/// - 真机：通过 IOHIDEvent 注入硬件级触摸事件，走完整 UIKit 事件派发路径。
/// - 模拟器：用 #if targetEnvironment(simulator) 编译期切换，改用 ObjC 运行时触发手势识别器。
///   Apple Silicon Simulator 的 .simruntime 中 iOS IOKit 确实存在 IOHIDEventCreateDigitizerFingerEvent，
///   但 _enqueueHIDEvent: 在模拟器进程内无法实际注入触摸事件，因此必须用编译期宏而非运行时判断。
enum TouchInjector {

// MARK: - 真机路径：IOHIDEvent 注入
#if !targetEnvironment(simulator)

    private static let kEventRange:    UInt32 = 0x00000001
    private static let kEventTouch:    UInt32 = 0x00000002
    private static let kEventPosition: UInt32 = 0x00000100

    private typealias CreateFingerEventFn = @convention(c) (
        CFAllocator?, UInt64, UInt32, UInt32, UInt32,
        Double, Double, Double, Double, Double,
        UInt8, UInt8, UInt32
    ) -> CFTypeRef?

    private static let createFingerEvent: CreateFingerEventFn? = {
        let candidates: [String?] = [
            nil,
            "/System/Library/Frameworks/IOKit.framework/IOKit",
            "/System/Library/PrivateFrameworks/IOKit.framework/IOKit",
        ]
        for path in candidates {
            let handle: UnsafeMutableRawPointer?
            if let p = path { handle = dlopen(p, RTLD_LAZY | RTLD_LOCAL) }
            else { handle = dlopen(nil, RTLD_LAZY) }
            if let h = handle, let sym = dlsym(h, "IOHIDEventCreateDigitizerFingerEvent") {
                return unsafeBitCast(sym, to: CreateFingerEventFn.self)
            }
        }
        return nil
    }()

    private static func injectHID(phase: UITouch.Phase, at point: CGPoint, touchId: UInt32 = 1) {
        guard let fn = createFingerEvent else { return }
        let mask: UInt32; let range: UInt8; let touch: UInt8
        switch phase {
        case .began:   mask = kEventRange | kEventTouch; range = 1; touch = 1
        case .moved:   mask = kEventPosition;            range = 1; touch = 1
        case .ended, .cancelled: mask = kEventRange | kEventTouch; range = 0; touch = 0
        default: return
        }
        let screen = UIScreen.main.bounds
        let nx = Double(point.x) / Double(screen.width)
        let ny = Double(point.y) / Double(screen.height)
        guard let event = fn(
            kCFAllocatorDefault, mach_absolute_time(),
            touchId, touchId, mask, nx, ny, 0, 0, 0, range, touch, 0
        ) else { return }
        UIApplication.shared.perform(NSSelectorFromString("_enqueueHIDEvent:"), with: event as AnyObject)
    }

    static func tap(at point: CGPoint, on sourceView: UIView? = nil, touchId: UInt32 = 1, completion: (() -> Void)? = nil) {
        injectHID(phase: .began, at: point, touchId: touchId)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            injectHID(phase: .ended, at: point, touchId: touchId)
            completion?()
        }
    }

    static func longPress(at point: CGPoint, on sourceView: UIView? = nil, durationMs: Int, completion: @escaping () -> Void) {
        injectHID(phase: .began, at: point)
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(durationMs)) {
            injectHID(phase: .ended, at: point)
            completion()
        }
    }

    static func doubleTap(at point: CGPoint, on sourceView: UIView? = nil, completion: @escaping () -> Void) {
        tap(at: point) {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self.tap(at: point, completion: completion)
            }
        }
    }

    static func swipe(
        from start: CGPoint, to end: CGPoint,
        on sourceView: UIView? = nil,
        durationMs: Int, completion: (() -> Void)? = nil
    ) {
        let steps = 20
        let interval = Double(durationMs) / 1000.0 / Double(steps)
        injectHID(phase: .began, at: start)
        for i in 1...steps {
            let t = CGFloat(i) / CGFloat(steps)
            let p = CGPoint(x: start.x + (end.x - start.x) * t,
                            y: start.y + (end.y - start.y) * t)
            DispatchQueue.main.asyncAfter(deadline: .now() + interval * Double(i)) {
                self.injectHID(phase: .moved, at: p)
                if i == steps {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                        self.injectHID(phase: .ended, at: p)
                        completion?()
                    }
                }
            }
        }
    }

// MARK: - 模拟器路径：ObjC 运行时触发手势识别器
#else

    /// 直接调用 gestureRecognizer 的 target-action，同时通过 KVC 设置 state 使 handler 读取正确。
    static func invokeGestureActions(_ recognizer: UIGestureRecognizer, state: UIGestureRecognizer.State) {
        recognizer.setValue(state.rawValue, forKey: "state")
        guard let targetEntries = recognizer.value(forKey: "_targets") as? [AnyObject] else { return }
        for entry in targetEntries {
            guard let target = entry.value(forKey: "_target") as? NSObject else { continue }
            // _action ivar 存储 SEL（C 指针），必须用 UnsafeRawPointer.load(as:) 读取，不能用 Optional<Selector>
            guard let ivar = class_getInstanceVariable(object_getClass(entry), "_action") else { continue }
            let sel = UnsafeRawPointer(Unmanaged.passUnretained(entry).toOpaque())
                .advanced(by: ivar_getOffset(ivar))
                .load(as: Selector.self)
            guard target.responds(to: sel) else { continue }
            _ = target.perform(sel, with: recognizer)
        }
    }

    static func tap(at point: CGPoint, on sourceView: UIView? = nil, touchId: UInt32 = 1, completion: (() -> Void)? = nil) {
        if let src = sourceView {
            if let gr = src.gestureRecognizers?.compactMap({ $0 as? UITapGestureRecognizer })
                .first(where: { $0.numberOfTapsRequired == 1 }) {
                invokeGestureActions(gr, state: .ended)
            } else if let ctrl = src as? UIControl {
                ctrl.sendActions(for: .touchUpInside)
            }
        }
        completion?()
    }

    static func longPress(at point: CGPoint, on sourceView: UIView? = nil, durationMs: Int, completion: @escaping () -> Void) {
        guard let src = sourceView,
              let gr = src.gestureRecognizers?.first(where: { $0 is UILongPressGestureRecognizer }) else {
            completion(); return
        }
        invokeGestureActions(gr, state: .began)
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(durationMs)) {
            self.invokeGestureActions(gr, state: .ended)
            completion()
        }
    }

    static func doubleTap(at point: CGPoint, on sourceView: UIView? = nil, completion: @escaping () -> Void) {
        if let src = sourceView,
           let gr = src.gestureRecognizers?.compactMap({ $0 as? UITapGestureRecognizer })
            .first(where: { $0.numberOfTapsRequired == 2 }) {
            invokeGestureActions(gr, state: .ended)
        }
        completion()
    }

    static func swipe(
        from start: CGPoint, to end: CGPoint,
        on sourceView: UIView? = nil,
        durationMs: Int, completion: (() -> Void)? = nil
    ) {
        let dx = end.x - start.x; let dy = end.y - start.y
        let dir: UISwipeGestureRecognizer.Direction
        if abs(dx) >= abs(dy) { dir = dx > 0 ? .right : .left }
        else { dir = dy > 0 ? .down : .up }
        if let src = sourceView,
           let gr = src.gestureRecognizers?.compactMap({ $0 as? UISwipeGestureRecognizer })
            .first(where: { $0.direction.contains(dir) }) {
            invokeGestureActions(gr, state: .ended)
        }
        completion?()
    }

#endif
}
