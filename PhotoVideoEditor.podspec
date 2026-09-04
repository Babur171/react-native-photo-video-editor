require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "PhotoVideoEditor"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/Babur171/react-native-photo-video-editor.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"
  # ios/Tests holds XCTest-based unit tests, not library sources — XCTest isn't linked into
  # consumer app targets, so these must never ship as part of the pod's normal build.
  s.exclude_files = "ios/Tests/**/*"

  install_modules_dependencies(s)
end
