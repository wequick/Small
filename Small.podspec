Pod::Spec.new do |s|

  s.name         = "Small"
  s.version      = "0.0.1"
  s.summary      = "A small framework to split app into small parts"
  s.homepage     = "https://github.com/wequick/Small"
  s.license      = "Apache License, Version 2.0"

  s.author             = { "galenlin" => "oolgloo.2012@gmail.com" }
  s.social_media_url   = "http://weibo.com/galenlin"

  s.platform     = :ios, "7.0"

  s.source       = { :git => "https://github.com/wequick/Small.git", :tag => "ios-#{s.version}" }

  s.source_files = "iOS/Small/*.{h,m}", "iOS/Small/Classes/*"
  s.public_header_files = "iOS/Small/*.h", "iOS/Small/Classes/*.h"
  s.private_header_files = "iOS/Small/Classes/_*.h"

  s.framework  = "UIKit"

  s.dependency "ZipArchive"
end
