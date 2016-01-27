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

#import "SMAppBundleLauncher.h"
#import "Small.h"

@implementation SMAppBundleLauncher

- (BOOL)preloadBundle:(SMBundle *)bundle
{
    if (bundle.type != SMBundleTypeApplication
        && bundle.type != SMBundleTypeHost) {
        return NO;
    }
    
    return YES;
}

- (void)loadBundle:(SMBundle *)bundle
{
    NSError *error = nil;
    [bundle loadAndReturnError:&error];
    if (error != nil) {
        NSLog(@"load bundle error: %@", error);
    }
}

- (void)launchBundle:(SMBundle *)bundle fromController:(UIViewController *)controller
{
    bundle.activeController = [self _controllerForBundle:bundle];
    [super launchBundle:bundle fromController:controller];
}

- (UIViewController *)controllerForBundle:(SMBundle *)bundle
{
    UIViewController *controller = [super controllerForBundle:bundle];
    NSString *controllerName = [[controller class] description];
    if (![bundle.path isEqualToString:controllerName] && ![[bundle.path stringByAppendingString:@"Controller"] isEqualToString:controllerName]) {
        // If not a main controller, recreate one
        bundle.activeController = [self _controllerForBundle:bundle];
    }
    return [super controllerForBundle:bundle];
}

- (UIViewController *)_controllerForBundle:(SMBundle *)bundle
{
    Class targetClazz = nil;
    UIStoryboard *targetBoard = nil;
    NSString *targetId = nil;
    if ([bundle.target isEqualToString:@""]) {
        targetClazz = bundle.principalClass;
    } else {
        NSString *target = bundle.target;
        NSInteger index = [target rangeOfString:@"/"].location;
        if (index != NSNotFound) {
            // Storyboard: "$storyboardName/$controllerId"
            NSString *storyboardName = [target substringToIndex:index];
            targetBoard = [UIStoryboard storyboardWithName:storyboardName bundle:bundle];
            targetId = [target substringFromIndex:index + 1];
        } else {
            // Controller: "$controllerName"
            targetClazz = [bundle classNamed:target];
            if (targetClazz == nil && !SMStringHasSuffix(target, @"Controller")) {
                targetClazz = [bundle classNamed:[target stringByAppendingString:@"Controller"]];
            }
        }
    }
    
    UIViewController *controller = nil;
    if (targetClazz != nil) {
        NSString *nibName = NSStringFromClass(targetClazz);
        NSString *nibPath = [bundle pathForResource:nibName ofType:@"nib"];
        if (nibPath != nil) {
            // Create controller from nib
            controller = [[targetClazz alloc] initWithNibName:nibName bundle:bundle];
        } else {
            // Create controller from class
            controller = [[targetClazz alloc] init];
        }
    } else if (targetBoard != nil) {
        // FIXME: Check if exists a storyboard contains the controller with the specific identifier
        @try {
            controller = [targetBoard instantiateViewControllerWithIdentifier:targetId];
        }
        @catch (NSException *exception) {
            
        }
        @finally {
            
        }
    }
    
    // Initialize controller parameters
    if (bundle.queryParams != nil) {
        [controller setValuesForKeysWithDictionary:bundle.queryParams];
    }
    
    return controller;
}

@end
