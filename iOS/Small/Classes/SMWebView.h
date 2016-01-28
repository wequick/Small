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

@interface SMWebView : UIWebView
{
    void (^_alertComplection)(NSInteger index);
}

@property (nonatomic, strong, readonly) NSURLRequest *originalRequest;
@property (nonatomic, readonly, getter=isHit) BOOL hit;

- (void)alert:(NSString *)message withTitle:(NSString *)title confirmButtonTitle:(NSString *)confirmButtonTitle complection:(void (^)(NSInteger index))complection;
- (NSInteger)confirm:(NSString *)message withTitle:(NSString *)title cancelButtonTitle:(NSString *)cancelButtonTitle otherButtonTitles:(NSArray *)othersButtonTitles complection:(void (^)(NSInteger index))complection;

@end
