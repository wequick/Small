/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

//______________________________________________________________________________
typedef NS_ENUM(NSUInteger, SMBundleType) {
    SMBundleTypeNone = 0,
    SMBundleTypeHost = 1,
    SMBundleTypeApplication = 2, // FWMK
    SMBundleTypeAssets = 3 // BNDL
};

//______________________________________________________________________________

@class SMBundleLauncher;

@interface SMBundle : NSBundle

@property (nonatomic, strong) NSString *pkg; // identifier
@property (nonatomic, strong) NSString *uri;
@property (nonatomic, strong) NSDictionary *rules;

@property (nonatomic, assign) SMBundleType type;
@property (nonatomic, strong) NSString *builtInPath;
@property (nonatomic, strong) NSString *patchPath;
@property (nonatomic, strong) NSString *patchFile;
@property (nonatomic, strong) NSString *versionName; // Bundle versions string, short
@property (nonatomic, strong) NSString *versionCode; // Bundle version

@property (nonatomic, strong) NSString *target;
@property (nonatomic, strong) NSString *query;

@property (nonatomic, strong) NSURL *URL;

@property (nonatomic, strong) NSString *src;
@property (nonatomic, strong, readonly) UIImage *icon;

@property (nonatomic, strong, readonly) NSDictionary *queryParams;
@property (nonatomic, strong) NSURL *targetURL;

@property (nonatomic, strong) NSString *path;

@property (nonatomic, assign) long long size;

@property (nonatomic, strong) UIViewController *mainController; // If specify `principal class' at `Info.plist', create an instance of the class, otherwise, create by class `[bundleName]Controller'
@property (nonatomic, strong) UIViewController *activeController;

+ (instancetype)bundleForName:(NSString *)name;
+ (void)setBaseUrl:(NSString *)url;
+ (void)loadLaunchableBundlesWithComplection:(void (^)(void))complection;
+ (NSArray *)allLaunchableBundles;
+ (instancetype)launchableBundleForURL:(NSURL *)url;
+ (void)registerLauncher:(SMBundleLauncher *)launcher;
+ (void)removeExternalBundles;

- (instancetype)initWithDictionary:(NSDictionary *)dictionary;

- (void)prepareForLaunch;
- (void)launchFromController:(UIViewController *)controller;
- (UIViewController *)launcherController;

- (BOOL)upgrade;

@end


UIKIT_STATIC_INLINE UIImage *SMBundleImage(NSBundle *bundle, NSString *imageName)
{
    UIImage *image = nil;
    if ([[[UIDevice currentDevice] systemVersion] floatValue] >= 8.0) {
        image = [UIImage imageNamed:imageName inBundle:bundle compatibleWithTraitCollection:nil];
//        NSLog(@"-- call imageNamed:inBundle:compatibleWithTraitCollection:");
    } else {
//        NSLog(@"-- call imageNamed");
        static NSMutableDictionary *sm_bundleCacheImages = nil;
        if (sm_bundleCacheImages == nil) {
            sm_bundleCacheImages = [NSMutableDictionary dictionary];
        }
        NSString *cacheKey = [NSString stringWithFormat:@"%@/%@", [bundle bundleIdentifier], imageName];
        UIImage *cacheImage = [sm_bundleCacheImages objectForKey:cacheKey];
        NSString *pureImageName = imageName;
        NSString *suffix = @"png";
        NSRange range = [pureImageName rangeOfString:@"."];
        if (range.length != 0) {
            pureImageName = [imageName substringToIndex:range.location];
            suffix = [imageName substringFromIndex:range.location + 1];
        }
        if (cacheImage == nil) {
            NSArray *configs = @[@"@3x", @"@2x", @""];
            NSArray *scales = @[@3, @2, @1];
            for (NSInteger index = 0; index < [configs count]; index++) {
                NSString *config = [configs objectAtIndex:index];
                CGFloat scale = [[scales objectAtIndex:index] floatValue];
                NSString *configImageName = [pureImageName stringByAppendingString:config];
                NSString *path = [bundle pathForResource:configImageName ofType:suffix];
                cacheImage = [UIImage imageWithContentsOfFile:path];
                if (cacheImage != nil) {
                    if (scale != cacheImage.scale) {
                        cacheImage = [UIImage imageWithCGImage:[cacheImage CGImage] scale:scale orientation:(cacheImage.imageOrientation)];
                    }
                    [sm_bundleCacheImages setObject:cacheImage forKey:cacheKey];
                    break;
                }
            }
        }
        image = cacheImage;
    }
    if (image == nil) {
        NSLog(@"Missing image %@ in bundle with identifier %@", imageName, [bundle bundleIdentifier]);
    } else {
//        NSLog(@"Load image %@ with scale %d", imageName, (int)image.scale);
    }
    return image;
}
