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

@interface UIViewController (SMNavigation)

@property (nonatomic, assign) BOOL fullscreen; // default is NO. if set to YES, hide navigation bar
@property (nonatomic, strong) UIViewController *sourceController; // for `fullscreen'
@property (nonatomic, strong) UIWindow *sourceWindow; // for `fullscreen'

- (UIBarButtonItem *)sm_backBarItemWithTitle:(NSString *)title target:(id)target action:(SEL)action;
- (void)sm_setBackBarButtonItemWithTitle:(NSString *)title target:(id)target action:(SEL)action;

@end
