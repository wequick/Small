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

#import "SMWebBundleLauncher.h"
#import "Small.h"
#import "SMWebController.h"
#import "SMWebView.h"

@interface SMWebBundleLauncher () <UIWebViewDelegate>

@end

@implementation SMWebBundleLauncher

DEF_SINGLETON(sharedLauncher)

- (BOOL)preloadBundle:(SMBundle *)bundle
{
    NSURL *url = [bundle URL];
    if (url == nil) {
        return NO;
    }
    NSString *scheme = [url scheme];
    if (![scheme isEqualToString:@"http"] &&
        ![scheme isEqualToString:@"https"] &&
        ![scheme isEqualToString:@"file"]) {
        return NO;
    }
    
    // Preload web data
    if ([NSThread isMainThread]) {
        SMWebController *controller = [self _controllerForBundle:bundle];
        bundle.mainController = controller;
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            SMWebController *controller = [self _controllerForBundle:bundle];
            bundle.mainController = controller;
        });
    }
    
    return YES;
}

- (void)launchBundle:(SMBundle *)bundle fromController:(UIViewController *)controller
{
    SMWebController *nextController = (id) bundle.mainController;
    if ([controller conformsToProtocol:@protocol(SMWebControllerDelegate)]) {
        nextController.delegate = (id)controller;
    }
    
    bundle.activeController = nextController;
    [super launchBundle:bundle fromController:controller];
}

- (UIViewController *)controllerForBundle:(SMBundle *)bundle
{
    return [super controllerForBundle:bundle];
}

- (SMWebController *)_controllerForBundle:(SMBundle *)bundle
{
    SMWebView *webView = [[SMWebView alloc] initWithFrame:[UIScreen mainScreen].bounds];
    SMWebController *nextController = [[SMWebController alloc] init];
    webView.delegate = nextController;
    nextController.webView = webView;
    dispatch_async(dispatch_get_global_queue(0, 0), ^{
        [webView loadRequest:[NSURLRequest requestWithURL:bundle.URL]];
    });
    return nextController;
}

@end
