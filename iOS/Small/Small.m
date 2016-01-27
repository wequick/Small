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

#import "Small.h"
#import "SMBundleFetcher.h"
#import "SMWebBundleLauncher.h"
#import "SMAppBundleLauncher.h"
#import "SMUtils.h"

static NSString *const kBundleVersionsKey = @"SMBundleVersions";
static NSString *const kBundleUpgradeUrlsKey = @"SMBundleUpgradeUrls";
static NSString *const kHostVersionKey = @"SMHostVersion";
static NSString *const kSmallScheme = @"small";

static BOOL kIsLaunchingNewHostApp = NO;
static NSInteger kFetchingBundleIndex = 0;
static NSMutableArray *kFetchingBundles = nil;
static SMBundleUpgradeMode kUpgradeMode = SMBundleUpgradeModeSetup|SMBundleUpgradeModeBackground;
static NSMutableDictionary *kJSHandlers = nil;
static NSMutableDictionary *kActionHandlers = nil;
static NSURL *kBaseUrl = nil;

@implementation Small

+ (void)setBundleUpgradeMode:(SMBundleUpgradeMode)mode
{
    kUpgradeMode = mode;
}

+ (void)setUpWithComplection:(void (^)(void))complection
{
    kFetchingBundleIndex = 0;
    kFetchingBundles = nil;
    [self checkHostVersion];
    [SMBundle registerLauncher:[SMAppBundleLauncher new]];
    [SMBundle registerLauncher:[SMWebBundleLauncher new]];
    [SMBundle loadLaunchableBundlesWithComplection:complection];
}

+ (void)setBaseUri:(NSString *)uri
{
    if (uri == nil) {
        kBaseUrl = nil;
        return;
    }
    kBaseUrl = [NSURL URLWithString:uri];
}

+ (NSString *)absoluteUriFromUri:(NSString *)uri
{
    return [[self urlFromUri:uri] absoluteString];
}

+ (NSURL *)urlFromUri:(NSString *)uri
{
    NSURL *url = [NSURL URLWithString:uri];
    if (kBaseUrl == nil || url.scheme != nil) return url;
    return [NSURL URLWithString:uri relativeToURL:kBaseUrl];
}

+ (void)openUri:(NSString *)uri fromView:(UIView *)view
{
    NSURL *url = [self urlFromUri:uri];
    [self openURL:url fromView:view];
}

+ (void)openURL:(NSURL *)url fromView:(UIView *)view
{
    NSString *scheme = url.scheme;
    if ([scheme isEqualToString:kSmallScheme]) {
        // If has registered a `small' action, triggered it
        void (^handler)(UIView *sender, NSDictionary *) = [self handlerForAction:url.host];
        if (handler != nil) {
            NSDictionary *querys = [SMUtils querysWithString:url.query];
            handler(view, querys);
            return;
        }
    } else if (![SMUtils isBundleScheme:scheme]) {
        // If has registered an application scheme, go for it
        if ([[UIApplication sharedApplication] canOpenURL:url]) {
            [[UIApplication sharedApplication] openURL:url];
            return;
        }
    }
    
    // If has registered a bundle, launch it
    SMBundle *bundle = [SMBundle launchableBundleForURL:url];
    if (bundle == nil) return;
    
    id controller = [view nextResponder];
    while (![controller isKindOfClass:[UIViewController class]]) {
        controller = [controller nextResponder];
        if (controller == nil) {
            break;
        }
    }
    if (controller == nil) return;
    
    [bundle launchFromController:controller];
}

+ (void)openUri:(NSString *)uri fromController:(UIViewController *)controller
{
    NSURL *url = [self urlFromUri:uri];
    [self openURL:url fromController:controller];
}

+ (void)openURL:(NSURL *)url fromController:(UIViewController *)controller
{
    if (controller == nil) return;
    
    if (![SMUtils isBundleScheme:url.scheme]) {
        // If has registered an application scheme, go for it
        if ([[UIApplication sharedApplication] canOpenURL:url]) {
            [[UIApplication sharedApplication] openURL:url];
            return;
        }
    }
    
    SMBundle *bundle = [SMBundle launchableBundleForURL:url];
    if (bundle == nil) return;
    
    [bundle launchFromController:controller];
}

+ (UIViewController *)controllerForUri:(NSString *)uri
{
    NSURL *url = [self urlFromUri:uri];
    return [self controllerForURL:url];
}

+ (UIViewController *)controllerForURL:(NSURL *)url
{
    // If has registered application scheme, go for it
    if (![[url scheme] isEqualToString:@"http"]
        && [[UIApplication sharedApplication] canOpenURL:url]) {
        return nil;
    }
    
    SMBundle *bundle = [SMBundle launchableBundleForURL:url];
    if (bundle != nil) {
        return [bundle launcherController];
    }
    
    return nil;
}

+ (NSArray *)allBundles
{
    return [SMBundle allLaunchableBundles];
}

#pragma mark - Upgrade

+ (void)checkHostVersion {
    NSString *originalVersion = [[NSUserDefaults standardUserDefaults] objectForKey:kHostVersionKey];
    NSString *currentVersion = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleShortVersionString"];
    if (![originalVersion isEqualToString:currentVersion]) {
        kIsLaunchingNewHostApp = YES;
        [[NSUserDefaults standardUserDefaults] setObject:currentVersion forKey:kHostVersionKey];
        // Clear cache
        [SMBundle removeExternalBundles];
    } else {
        kIsLaunchingNewHostApp = NO;
    }
}

+ (BOOL)isLaunchingNewHostApp
{
    return kIsLaunchingNewHostApp;
}

+ (NSDictionary *)bundleVersions
{
    return [[NSUserDefaults standardUserDefaults] objectForKey:kBundleVersionsKey];
}

+ (void)setBundleVersion:(NSString *)version forBundleName:(NSString *)name
{
    NSMutableDictionary *dict = [[NSMutableDictionary alloc] initWithDictionary:[[NSUserDefaults standardUserDefaults] objectForKey:kBundleVersionsKey]];
    [dict setObject:version forKey:name];
    [[NSUserDefaults standardUserDefaults] setObject:dict forKey:kBundleVersionsKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

+ (NSDictionary *)bundleUpgradeUrlsForMode:(SMBundleUpgradeMode)mode
{
    if (mode & kUpgradeMode) {
        return [[NSUserDefaults standardUserDefaults] objectForKey:kBundleUpgradeUrlsKey];
    }
    return nil;
}

+ (void)setBundleUpgradeUrls:(NSDictionary *)urls
{
    if (urls == nil) {
        [[NSUserDefaults standardUserDefaults] removeObjectForKey:kBundleUpgradeUrlsKey];
    } else {
        [[NSUserDefaults standardUserDefaults] setObject:urls forKey:kBundleUpgradeUrlsKey];
    }
    [[NSUserDefaults standardUserDefaults] synchronize];
}

+ (void)performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler
{
    [self _performFetchWithCompletionHandler:completionHandler];
}

+ (void)_performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    NSDictionary *upgradeUrls = [self bundleUpgradeUrlsForMode:SMBundleUpgradeModeBackground];
    if ([upgradeUrls count] == 0) {
        completionHandler(UIBackgroundFetchResultNoData);
        return;
    }
    NSString *aName = [[upgradeUrls allKeys] firstObject];
    NSString *anUrl = upgradeUrls[aName];
    [[SMBundleFetcher defaultFetcher] fetchBundleOfName:aName withURL:[NSURL URLWithString:anUrl] received:nil completion:^(NSString *path, NSError *error) {
        if (error) {
            completionHandler(UIBackgroundFetchResultFailed);
        } else {
            NSMutableDictionary *urls = [NSMutableDictionary dictionaryWithDictionary:upgradeUrls];
            [urls removeObjectForKey:aName];
            [self setBundleUpgradeUrls:urls];
            return [self _performFetchWithCompletionHandler:completionHandler];
        }
    }];
}

#pragma mark - Web bridge

+ (void)registerJSMethod:(NSString *)method withHandler:(void (^)(NSDictionary *, JSValue *))handler
{
    if (kJSHandlers == nil) {
        kJSHandlers = [[NSMutableDictionary alloc] init];
    }
    [kJSHandlers setObject:handler forKey:method];
}

+ (void (^)(NSDictionary *, JSValue *))jsHandlerForMethod:(NSString *)method
{
    return [kJSHandlers objectForKey:method];
}

#pragma mark - Action

+ (void)registerAction:(NSString *)action withHandler:(void (^)(UIView *sender, NSDictionary *parameters))handler
{
    if (kActionHandlers == nil) {
        kActionHandlers = [[NSMutableDictionary alloc] init];
    }
    [kActionHandlers setObject:handler forKey:action];
}

+ (void (^)(UIView *sender, NSDictionary *parameters))handlerForAction:(NSString *)action
{
    return [kActionHandlers objectForKey:action];
}

@end
