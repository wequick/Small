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

#import "SMWebView.h"

@implementation SMWebView
{
    NSURLRequest *_originalRequest;
}

@synthesize originalRequest = _originalRequest;

static const NSInteger kTagSystemAlert = -1;
static const NSInteger kTagSystemConfirm = -2;
static const NSInteger kTagUserAlert = -3;
static const NSInteger kTagUserConfirm = -4;
static const NSInteger kTagCancel = 0;
static const NSInteger kTagOK = 1;

- (instancetype)initWithFrame:(CGRect)frame
{
    if (self = [super initWithFrame:frame]) {
        
    }
    return self;
}

- (void)loadRequest:(NSURLRequest *)request {
    [super loadRequest:request];
    _originalRequest = request;
}

- (void)reload {
    [self loadRequest:_originalRequest];
}

- (void)dealloc {
    _originalRequest = nil;
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
    if (!_hit) _hit = true;
    return [super hitTest:point withEvent:event];
}

- (void)alert:(NSString *)message withTitle:(NSString *)title confirmButtonTitle:(NSString *)confirmButtonTitle complection:(void (^)(NSInteger index))complection
{
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:title message:message delegate:self cancelButtonTitle:confirmButtonTitle otherButtonTitles:nil, nil];
    if (complection) {
        _alertComplection = complection;
        [alert setTag:kTagUserAlert];
        [alert show];
    } else {
        // Wait for user interaction
        [alert setTag:kTagSystemAlert];
        [alert show];
        while ([alert tag] == kTagSystemAlert) {
            [[NSRunLoop mainRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01f]];
        }
    }
}

- (NSInteger)confirm:(NSString *)message withTitle:(NSString *)title cancelButtonTitle:(NSString *)cancelButtonTitle otherButtonTitles:(NSArray *)otherButtonTitles complection:(void (^)(NSInteger))complection
{
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:title message:message delegate:self cancelButtonTitle:cancelButtonTitle otherButtonTitles:nil, nil];
    for (NSString *otherButtonTitle in otherButtonTitles) {
        [alert addButtonWithTitle:otherButtonTitle];
    }
    if (complection) {
        _alertComplection = complection;
        [alert setTag:kTagUserConfirm];
        [alert show];
    } else {
        // Wait for user interaction
        [alert setTag:kTagSystemConfirm];
        [alert show];
        while ([alert tag] == kTagSystemConfirm) {
            [[NSRunLoop mainRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01f]];
        }
    }
    return [alert tag];
}

#pragma mark - Alert|Confirm from Javascript

- (void)webView:(UIWebView *)sender runJavaScriptAlertPanelWithMessage:(NSString *)message initiatedByFrame:(id)frame {
    [self alert:message withTitle:nil confirmButtonTitle:NSLocalizedString(@"OK", nil) complection:nil];
}

- (BOOL)webView:(UIWebView *)sender runJavaScriptConfirmPanelWithMessage:(NSString *)message initiatedByFrame:(id)frame {
    NSInteger index = [self confirm:message withTitle:nil cancelButtonTitle:NSLocalizedString(@"Cancel", nil) otherButtonTitles:@[NSLocalizedString(@"OK", nil)] complection:nil];
    return (index != kTagCancel);
}

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
    switch ([alertView tag]) {
        case kTagSystemAlert:
            [alertView setTag:kTagOK];
            break;
        case kTagUserAlert:
            [alertView setTag:kTagOK];
            if (_alertComplection) {
                _alertComplection(0);
                _alertComplection = nil;
            }
            break;
        case kTagSystemConfirm:
            if (buttonIndex == [alertView cancelButtonIndex]) {
                [alertView setTag:kTagCancel];
            } else {
                [alertView setTag:buttonIndex];
            }
            break;
        case kTagUserConfirm:
            if (buttonIndex == [alertView cancelButtonIndex]) {
                [alertView setTag:kTagCancel];
            } else {
                [alertView setTag:buttonIndex];
            }
            if (_alertComplection) {
                _alertComplection([alertView tag]);
                _alertComplection = nil;
            }
            break;
            
        default:
            break;
    }
}

@end
