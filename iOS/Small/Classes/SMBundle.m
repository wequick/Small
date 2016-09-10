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

#import "SMBundle.h"
#import "SMFileManager.h"
#import "ZipArchive.h"
#import "Small.h"
#import "SMBundleFetcher.h"
#import "SMBundleLauncher.h"

NSString *const SMBundleManifestName        = @"bundle.json";
NSString *const SMBundleLoaderErrorDomain   = @"SMBundleLoaderError";
NSString *const SMBundlesKey                = @"bundles";

NSInteger const SMBundleLoaderMissingFileError    = 1;
NSInteger const SMBundleLoaderFormatError         = 2;
NSInteger const SMBundleLoaderUnknownVersionError = 3;

static NSMutableArray *kBundleLaunchers;
static NSMutableArray *kLaunchableBundles;
static void (^kLoadingComplection)(void);
static NSMutableDictionary *kBundleDownloadPaths;
static NSString *kBaseUrl;
static NSArray *kRegisteredBundles;
static NSArray *kDownloadBundles;

@interface SMBundle ()
{
    SMBundleLauncher *_applicableLauncher;
}

@end

@implementation SMBundle

+ (instancetype)bundleForName:(NSString *)name
{
    if (kLaunchableBundles == nil) return nil;
    for (SMBundle *bundle in kLaunchableBundles) {
        if ([bundle.pkg isEqualToString:name]) return bundle;
    }
    return nil;
}

+ (void)setBaseUrl:(NSString *)url
{
    kBaseUrl = url;
}

+ (void)loadLaunchableBundlesWithComplection:(void (^)(void))complection
{
    kLoadingComplection = complection;
    NSError *error = nil;
    [self loadManifestWithPath:[SMFileManager documentBundlesPath] error:&error];
    if (error) {
        [self loadManifestWithPath:[SMFileManager mainBundlesPath] error:&error];
    }
}

+ (NSArray *)allLaunchableBundles
{
    return kLaunchableBundles;
}

+ (instancetype)launchableBundleForURL:(NSURL *)url
{
    for (SMBundle *bundle in kLaunchableBundles) {
        // Look up the matched bundle
        NSString *target;
        NSString *query;
        if ([bundle matchesRuleForURL:url target:&target query:&query]) {
            [bundle setTarget:target];
            [bundle setQuery:query];
            return bundle;
        }
    }
    
    NSString *scheme = url.scheme;
    if ([scheme isEqualToString:@"http"] ||
        [scheme isEqualToString:@"https"] ||
        [scheme isEqualToString:@"file"]) {
        // Downgrade to launch remote web page
        SMBundle *bundle = [[SMBundle alloc] init];
        [bundle setURL:url];
        [bundle prepareForLaunch];
        return bundle;
    }
    
    return nil;
}

+ (void)registerLauncher:(SMBundleLauncher *)launcher
{
    if (kBundleLaunchers == nil) {
        kBundleLaunchers = [[NSMutableArray alloc] init];
    }
    
    [kBundleLaunchers addObject:launcher];
}

+ (void)removeExternalBundles
{
    NSString *documentBundlesPath = [SMFileManager documentBundlesPath];
    if ([[SMFileManager defaultManager] fileExistsAtPath:documentBundlesPath]) {
        [[SMFileManager defaultManager] removeItemAtPath:documentBundlesPath error:nil];
    }
    NSString *documentTempPath = [SMFileManager documentTempPath];
    if ([[SMFileManager defaultManager] fileExistsAtPath:documentTempPath]) {
        [[SMFileManager defaultManager] removeItemAtPath:documentTempPath error:nil];
    }
}

- (BOOL)upgrade {
    if (_applicableLauncher == nil) return NO;
//    return [_applicableLauncher upgradeBundle:self];
    
    if ([self isLoaded]) [self unload];
    
    // Unzip the patch file
    ZipArchive *zipArchive = [[ZipArchive alloc] init];
    [zipArchive UnzipOpenFile:self.patchFile];
    [zipArchive UnzipFileTo:[SMFileManager documentBundlesPath] overWrite:YES];
    [zipArchive UnzipCloseFile];
    [[NSFileManager defaultManager] removeItemAtPath:self.patchFile error:nil];
    
    SMBundle *bundle = [[SMBundle alloc] initWithPath:self.patchPath];
    NSDictionary *info = bundle.infoDictionary;
    bundle.pkg = self.pkg;
    bundle.uri = self.uri;
    bundle.rules = self.rules;
    bundle.type = self.type;
    bundle.patchPath = self.patchPath;
    bundle.patchFile = self.patchFile;
    bundle.versionName = info[@"CFBundleShortVersionString"];
    bundle.versionCode = info[@"CFBundleVersion"];
    bundle->_applicableLauncher = _applicableLauncher;
    
    [kLaunchableBundles removeObject:self];
    [kLaunchableBundles addObject:bundle];
    
//    UIViewController *v = [UIApplication sharedApplication].delegate.window.rootViewController;
//    UITabBarController *tc = (id) v.presentedViewController;
//    UINavigationController *nc = (id) tc.childViewControllers[0];
//    UIViewController *v0 = nc.childViewControllers[0];
//    
//    Class cls = v0.class;
//    NSString *name = NSStringFromClass(cls);
//    
//    // Hot swap
//    [self unload];
//    NSMutableArray *bundles = (NSMutableArray *)[NSBundle allFrameworks];
//    [bundles removeObject:self];
//    
//    NSError *error;
//    BOOL succeed = [bundle loadAndReturnError:&error];
//    if (!succeed) {
//        return NO;
//    }
//    
//    Class cls2 = NSClassFromString(name); //cls;//[bundle classNamed:name];
//    
//    UIViewController *v0_ = [[cls2 alloc] initWithNibName:name bundle:bundle];
//    v0_.tabBarItem = v0.tabBarItem;
////    [nc popToRootViewControllerAnimated:NO];
//    dispatch_async(dispatch_get_main_queue(), ^{
//        nc.viewControllers = @[v0_];
////        [nc popToRootViewControllerAnimated:NO];
////        [nc pushViewController:v0_ animated:YES];
//    });
    
    return YES;
}

- (BOOL)matchesRuleForURL:(NSURL *)url target:(NSString * __autoreleasing*)outTarget query:(NSString * __autoreleasing*)outQuery {
    /* e.g.
     *  input
     *      - url: http://host/path/abc.html
     *      - self.uri: http://host/path
     *      - self.rules: abc.html -> AbcController
     *  output
     *      - target => AbcController
     */
    NSString *query = [url query];
    NSString *urlString = [url absoluteString];
    NSRange range = [urlString rangeOfString:self.uri];
    if (range.length == 0) return NO;
    
    NSString *from = [urlString substringFromIndex:range.length];
    NSString *excludedQueryFrom = nil;
    if (query != nil) {
        excludedQueryFrom = [from substringToIndex:[from length] - [query length] - 1];
    }
    for (NSString *key in self.rules) {
        // TODO: regex match and replace
        NSString *matchingTarget = nil;
        if ([key isEqualToString:from]) {
            matchingTarget = [self.rules objectForKey:key];
        } else if (excludedQueryFrom != nil && [key isEqualToString:excludedQueryFrom]) {
            matchingTarget = [self.rules objectForKey:key];
        }
        if (matchingTarget == nil) continue;
        
        NSRange range = [matchingTarget rangeOfString:@"?"];
        if (range.length != 0) {
            *outTarget = [matchingTarget substringToIndex:range.location];
            if (query != nil) {
                *outQuery = [NSString stringWithFormat:@"%@&%@", query, [matchingTarget substringFromIndex:range.location + 1]];
            } else {
                *outQuery = [matchingTarget substringFromIndex:range.location + 1];
            }
        } else {
            *outTarget = matchingTarget;
            *outQuery = query;
        }
        return YES;
    }
    
    return NO;
}

- (instancetype)initWithDictionary:(NSDictionary *)dictionary
{
    NSString *pkg = [dictionary objectForKey:@"pkg"];
    if (pkg == nil || [pkg isEqualToString:@"main"]) {
        if (self = [super initWithPath:[[NSBundle mainBundle] bundlePath]]) {
            self.type = SMBundleTypeHost;
        }
        return self;
    }
    
    NSString *bundlePath = nil;
    NSString *bundleSuffix = @"bundle";
    SMBundleType bundleType = SMBundleTypeAssets;
    if ([pkg rangeOfString:@".app."].location != NSNotFound
        || [pkg rangeOfString:@".lib."].location != NSNotFound) {
        bundleSuffix = @"framework";
        bundleType = SMBundleTypeApplication;
    }
    
    NSString *bundleName = [pkg stringByReplacingOccurrencesOfString:@"." withString:@"_"];
    bundleName = [bundleName stringByAppendingFormat:@".%@", bundleSuffix];
    NSString *documentBundlesPath = [SMFileManager documentBundlesPath];
    NSString *patchFilePath = [SMFileManager tempBundlePathForName:bundleName];
    if ([[NSFileManager defaultManager] fileExistsAtPath:patchFilePath]) {
        // Unzip
        NSString *unzipPath = documentBundlesPath;
        ZipArchive *zipArchive = [[ZipArchive alloc] init];
        [zipArchive UnzipOpenFile:patchFilePath];
        [zipArchive UnzipFileTo:unzipPath overWrite:YES];
        [zipArchive UnzipCloseFile];
        [[NSFileManager defaultManager] removeItemAtPath:patchFilePath error:nil];
    }
    NSString *patchPath = [documentBundlesPath stringByAppendingPathComponent:bundleName];
    NSString *builtinPath = [[SMFileManager mainBundlesPath] stringByAppendingPathComponent:bundleName];
    NSArray *bundlePaths = @[patchPath, builtinPath];
    for (NSString *aBundlePath in bundlePaths) {
        if ([[NSFileManager defaultManager] fileExistsAtPath:aBundlePath]) {
            bundlePath = aBundlePath;
            break;
        }
    }
    
    if (self = [super initWithPath:bundlePath]) {
        NSDictionary *info = self.infoDictionary;
        self.versionName = info[@"CFBundleShortVersionString"];
        self.versionCode = info[@"CFBundleVersion"];
        self.pkg = pkg;
        self.type = bundleType;
        _patchFile = patchFilePath;
        _patchPath = patchPath;
//        NSLog(@"-- %@: %@ %@", self.pkg, self.versionName, bundlePath);
        [self initValuesWithDictionary:dictionary];
    }
    return self;
}

- (void)initValuesWithDictionary:(NSDictionary *)dictionary {
    NSString *uri = [dictionary objectForKey:@"uri"];
    self.uri = [Small absoluteUriFromUri:uri];
    
    // UI routes to principal page
    NSString *defaultTarget = @"";
    NSMutableDictionary *rules = [[NSMutableDictionary alloc] init];
    rules[@""] = defaultTarget;
    rules[@".html"] = defaultTarget;
    rules[@"/index"] = defaultTarget;
    rules[@"/index.html"] = defaultTarget;
    // UI routes to other pages
    NSDictionary *userRules = [dictionary objectForKey:@"rules"];
    if (userRules != nil) {
        [rules setValuesForKeysWithDictionary:userRules];
    }
    self.rules = rules;
}

- (void)prepareForLaunch
{
    for (SMBundleLauncher *launcher in kBundleLaunchers) {
        if ([launcher resolveBundle:self]) {
            _applicableLauncher = launcher;
            if (self.versionCode != nil) {
                [Small setBundleVersion:self.versionCode forBundleName:self.pkg];
            }
            break;
        }
    }
}

- (void)launchFromController:(UIViewController *)controller
{
    if (_applicableLauncher == nil) {
        [self prepareForLaunch];
    }
    if (_applicableLauncher != nil) {
        [_applicableLauncher launchBundle:self fromController:controller];
    }
}

- (UIViewController *)launcherController
{
    if (_applicableLauncher == nil) {
        [self prepareForLaunch];
    }
    if (_applicableLauncher != nil) {
        return [_applicableLauncher controllerForBundle:self];
    }
    return nil;
}

- (NSURL *)URL
{
    if (_URL == nil) {
        NSURL *url = [self URLForResource:@"index" withExtension:@"html"];
        if (_query != nil) {
            url = [NSURL URLWithString:[NSString stringWithFormat:@"?%@", _query] relativeToURL:url];
        }
        return url;
    }
    return _URL;
}

- (UIImage *)icon
{
    NSString *iconName = [[[self infoDictionary] objectForKey:@"CFBundleIconFiles"] objectAtIndex:0] ?: @"icon";
    return SMBundleImage(self, iconName);
}

- (void)setQuery:(NSString *)query
{
    _query = query;
    if (query == nil) {
        _queryParams = nil;
    } else {
        NSMutableDictionary *params = [NSMutableDictionary dictionary];
        NSArray *components = [query componentsSeparatedByString:@"&"];
        for (NSString *component in components) {
            NSRange range = [component rangeOfString:@"="];
            if (range.length == 0) {
                continue;
            }
            
            NSString *key = [component substringToIndex:range.location];
            NSString *value = [component substringFromIndex:range.location + 1];
            [params setObject:value forKey:key];
        }
        _queryParams = params;
    }
}

//___________________________________________________________________________________________________
// Private

+ (BOOL)loadManifestWithPath:(NSString *)manifestPath error:(NSError *__autoreleasing*)error
{
    NSString *manifestFile = [manifestPath stringByAppendingPathComponent:SMBundleManifestName];
    NSData *manifestData = [NSData dataWithContentsOfFile:manifestFile];
    if (manifestData == nil) {
        if (error != nil) {
            *error = [[NSError alloc] initWithDomain:SMBundleLoaderErrorDomain code:SMBundleLoaderMissingFileError userInfo:nil];
        }
        return NO;
    }
    
    NSDictionary *manifest = [NSJSONSerialization JSONObjectWithData:manifestData options:NSJSONReadingMutableLeaves error:error];
    if (manifest == nil) {
        if (error != nil) {
            *error = [[NSError alloc] initWithDomain:SMBundleLoaderErrorDomain code:SMBundleLoaderFormatError userInfo:nil];
        }
        return NO;
    }
    
    // TODO: CRC
    //...
    
    NSString *version = [manifest objectForKey:@"version"];
    return [self loadManifest:manifest forVersion:version error:error];
}

+ (BOOL)loadManifest:(NSDictionary *)manifest forVersion:(NSString *)version error:(NSError * __autoreleasing*)error
{
    if ([version isEqualToString:@"1.0.0"]) {
        NSArray *bundles = [manifest objectForKey:SMBundlesKey];
        [self loadBundles:bundles];
        return YES;
    }
    
    if (error != nil) {
        *error = [[NSError alloc] initWithDomain:SMBundleLoaderErrorDomain code:SMBundleLoaderUnknownVersionError userInfo:nil];
    }
    return NO;
}

+ (void)loadBundles:(NSArray *)bundles {
    if (kLoadingComplection == nil) {
        [self syncLoadBundles:bundles];
    } else {
        dispatch_queue_t queue = dispatch_queue_create("net.wequick.small.BundleLoader", NULL);
        dispatch_async(queue, ^{
            [self syncLoadBundles:bundles];
            dispatch_sync(dispatch_get_main_queue(), ^{
                kLoadingComplection();
                kLoadingComplection = nil;
            });
        });
    }
}

+ (void)syncLoadBundles:(NSArray *)bundles {
    // Treat all bundles as local
    for (NSDictionary *desc in bundles) {
        SMBundle *bundle = [[SMBundle alloc] initWithDictionary:desc];
        if (bundle == nil) continue;
        
        [bundle prepareForLaunch];
        if (kLaunchableBundles == nil) {
            kLaunchableBundles = [[NSMutableArray alloc] init];
        }
        [kLaunchableBundles addObject:bundle];
    }
}

+ (long long)fileSizeWithURL:(NSURL *)url {
    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:url];
    [req setHTTPMethod:@"Head"];
    NSURLResponse *res = nil;
    NSError *error = nil;
    [NSURLConnection sendSynchronousRequest:req returningResponse:&res error:&error];
    if (error != nil || res == nil) {
        return 0;
    }
    return res.expectedContentLength;
}

+ (NSString *)zipPathForName:(NSString *)name {
    NSString *basePath = [SMFileManager documentTempPath]; // NSTemporaryDirectory()
    NSString *zipPath = [[basePath stringByAppendingPathComponent:name] stringByAppendingString:@".zip"];
    return zipPath;
}

- (void)setValue:(id)value forUndefinedKey:(NSString *)key {
    NSLog(@"-- SMBundle setValue:%@ forUndefinedKey:%@", value, key);
}

@end
