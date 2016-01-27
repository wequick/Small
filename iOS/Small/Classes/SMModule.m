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

#import "CYUIWebViewController.h"

@implementation SMModule

- (id)initWithDictionary:(NSDictionary *)dictionary
{
    if (self = [super init]) {
        self.name = [dictionary objectForKey:@"name"];
        self.uri = [dictionary objectForKey:@"uri"];
        self.src = [dictionary objectForKey:@"src"];
    }
    return self;
}

- (void)setUp
{
    if (self.name) {
        if ([self.name rangeOfString:@".framework"].length != 0) {
            [self initBundle:self.name needsLoad:YES];
        } else if ([self.name rangeOfString:@".bundle"].length != 0) {
            [self initBundle:self.name needsLoad:NO];
        }
    }
    
    if (_bundle == nil && self.src) {
        [self loadURL:self.src];
    }
    
    if (_bundle != nil) {
        _native = [_bundle pathForResource:@"index" ofType:@"html"] == nil;
    }
}

- (void)tearDown
{
    
}

- (void)initBundle:(NSString *)bundleName needsLoad:(BOOL)needsLoad
{
    NSError *error = nil;
    NSString *mainBundlePath = [[SMFileManager mainModulesPath] stringByAppendingPathComponent:bundleName];
    NSString *documentBundlePath = [[SMFileManager documentModulesPath] stringByAppendingPathComponent:bundleName];
    NSArray *bundlePaths = @[documentBundlePath, mainBundlePath];
    for (NSString *bundlePath in bundlePaths) {
        _bundle = [SMBundle bundleWithPath:bundlePath];
        if (needsLoad && _bundle != nil) {
            if ([_bundle loadAndReturnError:&error]) {
                NSLog(@"load framework success.");
            } else {
                NSLog(@"load framework err:%@", error);
            }
        }
    }
}

- (void)loadURL:(NSString *)url
{
    _bundle = [SMBundle bundleWithURL:[NSURL URLWithString:url]];
}

- (void)launchFromController:(UIViewController *)controller
{
    if (_native) {
        NSString *controllClassName = [_name substringToIndex:[_name length] - @".framework".length];
        Class controllClass = [_bundle classNamed:controllClassName];
//        UIViewController *controller = (id)[[controllClass alloc] init];
        if (controllClass != nil) {
            UIViewController *nextController = [[controllClass alloc] initWithNibName:controllClassName bundle:_bundle];
            [controller.navigationController pushViewController:nextController animated:YES];
        }
    } else {
        NSURL *url = [_bundle URLForResource:@"index" withExtension:@"html"];
        CYUIWebViewController *webController = [[CYUIWebViewController alloc] init];
        [webController setUrl:[url absoluteString]];
        [controller.navigationController pushViewController:webController animated:YES];
    }
}

@end
