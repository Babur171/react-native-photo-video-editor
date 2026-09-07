#import <PhotoVideoEditorSpec/PhotoVideoEditorSpec.h>
#import "PhotoVideoEditor-Swift.h"

// Objective-C cannot subclass a Swift class, so the TurboModule holds a
// `PhotoVideoEditorSwift` instance and forwards the spec methods to it.
@interface PhotoVideoEditor : NSObject <NativePhotoVideoEditorSpec>
@end

@implementation PhotoVideoEditor {
  PhotoVideoEditorSwift *_impl;
}

- (instancetype)init
{
  if (self = [super init]) {
    _impl = [PhotoVideoEditorSwift new];
  }
  return self;
}

- (void)openEditor:(NSString *)request
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject
{
  [_impl openEditor:request resolve:resolve reject:reject];
}

- (void)cancelExport:(NSString *)jobId
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  [_impl cancelExport:jobId resolve:resolve reject:reject];
}

- (NSNumber *)isAvailable
{
  return [_impl isAvailable];
}

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
