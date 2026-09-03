#import <PhotoVideoEditorSpec/PhotoVideoEditorSpec.h>
#import "PhotoVideoEditor-Swift.h"

@interface PhotoVideoEditor : PhotoVideoEditorSwift <NativePhotoVideoEditorSpec>
@end

@implementation PhotoVideoEditor

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativePhotoVideoEditorSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"PhotoVideoEditor";
}

@end
