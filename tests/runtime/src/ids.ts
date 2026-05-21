/**
 * View IDs anchored to the demo app Login screen.
 *
 * Android source: clients/android/demo/src/main/res/layout/activity_login.xml
 * iOS source:     clients/ios/demo/Sources/ClientToolsDemo/Login/LoginViewController.swift
 */
const PLATFORM = process.env.PLATFORM ?? "android";

export const IDS = {
  // ── Shared (same ID on both platforms) ────────────────────────────────────
  /** 登录按钮容器 (FrameLayout / UIButton, 52dp height) */
  SUBMIT_BTN: "login_btn_submit",
  /** 标题文本 "欢迎回来" (TextView / UILabel) — good for text modify tests */
  TITLE_TEXT: "login_text_title",
  /** 副标题文本 (TextView / UILabel) */
  SUBTITLE_TEXT: "login_text_subtitle",
  /** 输入区域容器 (LinearLayout / UIStackView) — NOT a text view, used for error test */
  INPUT_AREA: "login_input_area",
  /** 底部 Home 指示条 (View / UIView) */
  HOME_INDICATOR: "login_home_indicator",
  /** 关闭按钮 (TextView / UIButton) */
  CLOSE_BTN: "login_btn_close",

  // ── Platform-specific ─────────────────────────────────────────────────────
  /** 品牌 Logo 文字 "PULSE" */
  BRAND_TEXT: PLATFORM === "android" ? "login_logo_name" : "login_text_brand",
  /** 验证码 Tab 按钮 */
  TAB_SMS_BTN: PLATFORM === "android" ? "login_tab_code" : "login_tab_sms",
  /** 密码 Tab 按钮（点击仅切换 tab，无页面跳转） */
  TAB_PWD_BTN: PLATFORM === "android" ? "login_tab_password" : "login_tab_pwd",
  /** 可滚动视图（Android only，iOS login screen 无滚动容器 ID） */
  SCROLL_VIEW: PLATFORM === "android" ? "login_scroll" : (null as string | null),
} as const;
