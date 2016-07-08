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

#import "SMBundleLauncher.h"
#import "UIViewController+SMNavigation.h"

@implementation SMBundleLauncher

- (BOOL)resolveBundle:(SMBundle *)bundle
{
    if (![self preloadBundle:bundle]) {
        return NO;
    }
    [self loadBundle:bundle];
    return YES;
}

- (BOOL)preloadBundle:(SMBundle *)bundle
{
    return NO;
}

- (void)loadBundle:(SMBundle *)bundle
{
    
}

- (void)reloadBundle:(SMBundle *)bundle
{
    
}

- (void)launchBundle:(SMBundle *)bundle fromController:(UIViewController *)controller
{
    UIViewController *nextController = bundle.activeController;
    if (nextController == nil || controller == nil) {
        return;
    }
    // Initialize next controller parameters
    if (bundle.queryParams != nil) {
        [nextController setValuesForKeysWithDictionary:bundle.queryParams];
    }
    // If mark fullscreen, treat as intro view, add to a temp window and show it
    if (nextController.fullscreen) {
        nextController.sourceController = controller;
        UIWindow *window = [[[UIApplication sharedApplication] delegate] window];
        nextController.sourceWindow = window;
        UIWindow *tempWindow = [[UIWindow alloc] initWithFrame:window.bounds];
        [tempWindow addSubview:nextController.view];
        [[[UIApplication sharedApplication] delegate] setWindow:tempWindow];
        [tempWindow makeKeyAndVisible]; // Intro view, mark (1)
        return;
    }
    // FIXME: add some flag to specify launch method? (push|present|...)
    // Hide previous controller|view if necessary
    dispatch_block_t hidePreviousControllerBlock = nil;
    if (controller.sourceWindow != nil) {
        /* @see (1)
         * hide `Intro View' with animation
         */
        hidePreviousControllerBlock = ^{
            UIView *previousView = controller.view;
            [previousView removeFromSuperview];
            [controller.sourceWindow addSubview:previousView];
            [[[UIApplication sharedApplication] delegate] setWindow:controller.sourceWindow];
            [controller.sourceWindow makeKeyAndVisible];
            controller.sourceWindow = nil;
            controller.sourceController = nil;
            [UIView animateWithDuration:.5 animations:^{
                previousView.transform = CGAffineTransformMakeScale(2, 2);
                previousView.alpha = 0;
            } completion:^(BOOL finished) {
                [previousView removeFromSuperview];
            }];
        };
    }
    UIViewController *fromController = controller.sourceController ?: controller;
    if (fromController.navigationController != nil) { // Push
        [controller setHidesBottomBarWhenPushed:YES]; // hide tabbar
        
        if (hidePreviousControllerBlock) {
            [fromController.navigationController pushViewController:nextController animated:NO];
            hidePreviousControllerBlock();
        } else {
            [fromController.navigationController pushViewController:nextController animated:YES];
        }
        
        if ([[[[controller navigationController] viewControllers] firstObject] isEqual:controller]) {
            [controller setHidesBottomBarWhenPushed:NO]; // show tabbar
        }
    } else { // Present
        if (hidePreviousControllerBlock) {
            [fromController presentViewController:nextController animated:NO completion:hidePreviousControllerBlock];
        } else {
            [fromController presentViewController:nextController animated:YES completion:nil];
        }
    }
}

- (UIViewController *)controllerForBundle:(SMBundle *)bundle
{
    return bundle.activeController ?: bundle.mainController;
}

@end
