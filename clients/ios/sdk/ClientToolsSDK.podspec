Pod::Spec.new do |s|
  s.name             = 'ClientToolsSDK'
  s.version          = '1.0.1'
  s.summary          = 'AI Coding Client Tools SDK for iOS'
  s.description      = 'Runtime view inspection and modification for iOS apps'
  s.homepage         = 'https://github.com/Zzechen/client-tools'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'Zzechen' => 'zzcm1259@qq.com' }
  s.source           = { :git => 'https://github.com/Zzechen/client-tools.git', :tag => "ios/#{s.version}" }
  s.ios.deployment_target = '14.0'
  s.swift_version    = '5.0'
  s.source_files     = 'Sources/**/*.swift'
  s.frameworks      = 'UIKit', 'WebKit', 'Network'
  s.dependency 'SwiftProtobuf', '~> 1.28'
end
