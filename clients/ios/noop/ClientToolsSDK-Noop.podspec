Pod::Spec.new do |s|
  s.name             = 'ClientToolsSDK-Noop'
  s.version          = '1.0.1'
  s.summary          = 'Noop (release-safe) stub for ClientToolsSDK'
  s.description      = 'Drop-in replacement for ClientToolsSDK in Release builds. All methods are no-ops.'
  s.homepage         = 'https://github.com/Zzechen/client-tools'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'Zzechen' => 'zzcm1259@qq.com' }
  s.source           = { :git => 'https://github.com/Zzechen/client-tools.git', :tag => "ios/#{s.version}" }
  s.ios.deployment_target = '14.0'
  s.swift_version    = '5.0'
  s.source_files     = 'clients/ios/noop/Sources/**/*.swift'
end
