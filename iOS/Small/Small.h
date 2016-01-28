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

#import <UIKit/UIKit.h>

//! Project version number for Small.
FOUNDATION_EXPORT double SmallVersionNumber;

//! Project version string for Small.
FOUNDATION_EXPORT const unsigned char SmallVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <Small/PublicHeader.h>

#import "SMBundle.h"
#import "SMWebController.h"
#import "UIViewController+SMNavigation.h"

typedef NS_OPTIONS(NSUInteger, SMBundleUpgradeMode)
{
    SMBundleUpgradeModeSetup = 1, // upgrade bundles on [Small setUpWithLoader:]
    SMBundleUpgradeModeBackground = 1 << 1 // upgrade bundles on application's `Background Fetch'
};

@interface Small : NSObject

+ (void)setUpWithComplection:(void (^)(void))complection;

+ (void)setBaseUri:(NSString *)uri;
+ (NSString *)absoluteUriFromUri:(NSString *)uri;

+ (void)openUri:(NSString *)uri fromView:(UIView *)view;
+ (void)openURL:(NSURL *)url fromView:(UIView *)view;

+ (void)openUri:(NSString *)uri fromController:(UIViewController *)controller;
+ (void)openURL:(NSURL *)url fromController:(UIViewController *)controller;

+ (UIViewController *)controllerForUri:(NSString *)uri;
+ (UIViewController *)controllerForURL:(NSURL *)url;

+ (NSArray *)allBundles;

+ (NSDictionary *)bundleVersions;
+ (void)setBundleVersion:(NSString *)version forBundleName:(NSString *)name;
+ (NSDictionary *)bundleUpgradeUrlsForMode:(SMBundleUpgradeMode)mode;
+ (void)setBundleUpgradeUrls:(NSDictionary *)urls;

+ (void)setBundleUpgradeMode:(SMBundleUpgradeMode)mode;
+ (void)performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler;

+ (BOOL)isLaunchingNewHostApp;

// for SMWebView
+ (void)registerJSMethod:(NSString *)method withHandler:(void (^)(NSDictionary *parameters, JSValue *callback))handler;
+ (void (^)(NSDictionary *parameters, JSValue *callback))jsHandlerForMethod:(NSString *)method;

// for uri action - small://$action?k=v
+ (void)registerAction:(NSString *)action withHandler:(void (^)(UIView *sender, NSDictionary *parameters))handler;
+ (void (^)(UIView *sender, NSDictionary *parameters))handlerForAction:(NSString *)action;

@end

#define DEF_BUNDLE_RESOURCE2(_bundle_, _bundleClass_) \
UIKIT_STATIC_INLINE NSBundle *_bundle_##Bundle() \
{ \
    static NSBundle *bundle = nil; \
    static dispatch_once_t onceToken; \
    dispatch_once(&onceToken, ^{ \
        bundle = [NSBundle bundleForClass:[_bundleClass_ class]]; \
    }); \
    return bundle; \
} \
\
UIKIT_STATIC_INLINE UIImage *_bundle_##Image(NSString *imageName) \
{ \
    return SMBundleImage(_bundle_##Bundle(), imageName); \
} \
\
UIKIT_STATIC_INLINE NSString *_bundle_##String(NSString *aString) \
{ \
    return NSLocalizedStringFromTableInBundle(aString, @"Localizable", _bundle_##Bundle(), nil); \
}

#define DEF_BUNDLE_RESOURCE(_bundle_) DEF_BUNDLE_RESOURCE2(_bundle_, _bundle_##Controller)
