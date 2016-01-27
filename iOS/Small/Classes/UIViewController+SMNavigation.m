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

#import "UIViewController+SMNavigation.h"
#import "SMBackBarButtonItem.h"
#import <objc/runtime.h>

@implementation UIViewController (SMNavigation)

#pragma mark - BackButtonItem

- (UIBarButtonItem *)sm_backBarItemWithTitle:(NSString *)title target:(id)target action:(SEL)action
{
    if (title == nil) {
        NSArray *controllers = [[self navigationController] viewControllers];
        if ([controllers count] >= 2) {
            title = [[controllers objectAtIndex:controllers.count - 2] title];
        }
    }
    UIImage *image = nil;
    UIImageView *backIndicator = [[[[self navigationController] navigationBar] subviews] lastObject]; //_UINavigationBarBackIndicatorView
    if ([backIndicator isKindOfClass:[UIImageView class]]) {
        image = backIndicator.image;
    }
    SMBackBarButtonItem *backItem = [[SMBackBarButtonItem alloc] initWithTitle:title image:image target:target action:action];
    return backItem;
}

- (void)sm_setBackBarButtonItemWithTitle:(NSString *)title target:(id)target action:(SEL)action
{
    UIBarButtonItem *backItem = [self sm_backBarItemWithTitle:title target:target action:action];
    self.navigationItem.leftBarButtonItem = backItem;
    // Enable screen edge gesture for iOS7+
    UIGestureRecognizer *systemPopGesture = self.navigationController.interactivePopGestureRecognizer;
    NSArray *systemPopGestureTargets = [systemPopGesture valueForKey:@"_targets"];
    UIScreenEdgePanGestureRecognizer *customPopGesture = [[UIScreenEdgePanGestureRecognizer alloc] init];
    [customPopGesture setValue:systemPopGestureTargets forKey:@"_targets"];
    customPopGesture.edges = UIRectEdgeLeft;
    [systemPopGesture.view addGestureRecognizer:customPopGesture];
}

#pragma mark - Extension properties

static NSString *const kFullscreenKey = @"sm_fullscreen";
static NSString *const kSourceControllerKey = @"sm_sourceController";
static NSString *const kSourceWindowKey = @"sm_sourceWindow";

/* fullscreen */
- (void)setFullscreen:(BOOL)fullscreen {
    objc_setAssociatedObject(self, &kFullscreenKey, @(fullscreen), OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}
- (BOOL)fullscreen {
    NSNumber *number = objc_getAssociatedObject(self, &kFullscreenKey);
    return [number boolValue];
}
- (void)set_fullscreen:(BOOL)fullscreen {
    [self setFullscreen:fullscreen];
}
- (BOOL)_fullscreen {
    return [self fullscreen];
}

/* sourceController */
- (void)setSourceController:(UIViewController *)sourceController {
    objc_setAssociatedObject(self, &kSourceControllerKey, sourceController, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}
- (UIViewController *)sourceController {
    return objc_getAssociatedObject(self, &kSourceControllerKey);
}

/* sourceWindow */
- (void)setSourceWindow:(UIWindow *)sourceWindow {
    objc_setAssociatedObject(self, &kSourceWindowKey, sourceWindow, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}
- (UIWindow *)sourceWindow {
    return objc_getAssociatedObject(self, &kSourceWindowKey);
}

#pragma mark - KVO

- (void)setValue:(id)value forUndefinedKey:(NSString *)key {
    NSLog(@"%@ undefinedKey:%@", [[self class] description], key);
}

@end
