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

#import "SMBackBarButtonItem.h"

typedef void (*SMBackBarButtonOnClick)(id, SEL, id);

@implementation SMBackBarButtonItem
{
    UIButton *_backButton;
}

- (instancetype)initWithTitle:(NSString *)title image:(UIImage *)image target:(id)target action:(SEL)action
{
    UIButton *backButton = [UIButton buttonWithType:101];
    if (self = [super initWithCustomView:backButton]) {
        [self setTarget:target];
        [self setAction:action];
        [backButton addTarget:self action:@selector(backButtonClick:) forControlEvents:UIControlEventTouchUpInside];
        if ([title length] == 0) {
            title = @" "; // compat for iOS9, avoid left arrow going down
        }
        [backButton setTitle:title forState:UIControlStateNormal];
        [backButton setBackgroundColor:[UIColor clearColor]];
        if (image != nil) {
            [backButton setImage:image forState:UIControlStateNormal];
            [backButton setImageEdgeInsets:UIEdgeInsetsMake(0, -42, 0, 0)]; // FIXME: replace the magic number?
            [backButton setTitleEdgeInsets:UIEdgeInsetsMake(0, -30, 0, 0)];
        }
        _backButton = backButton;
    }
    
    return self;
}

- (void)dealloc
{
    _backButton = nil;
}

- (void)setTitle:(NSString *)title
{
    [_backButton setTitle:title forState:UIControlStateNormal];
}

- (void)setTintColor:(UIColor *)tintColor
{
    [_backButton setTintColor:tintColor];
}

- (void)backButtonClick:(id)sender {
    IMP imp = [self.target methodForSelector:self.action];
    SMBackBarButtonOnClick onClick = (SMBackBarButtonOnClick) imp;
    onClick(self.target, self.action, self);
}

@end
