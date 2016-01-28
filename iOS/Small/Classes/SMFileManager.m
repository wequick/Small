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

#import "SMFileManager.h"
#import "ZipArchive.h"

NSString *const SMBundleDirectoryName = @"bundles";
NSString *const SMTempDirectoryName = @"temp";

@implementation SMFileManager

+ (NSString *)mainBundlesPath
{
    NSString *path = [[NSBundle mainBundle] bundlePath];
    return path;
}

+ (NSString *)documentBundlesPath
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *path = [paths objectAtIndex:0];
    path = [path stringByAppendingPathComponent:SMBundleDirectoryName];
    if (![[self defaultManager] fileExistsAtPath:path]) {
        [[self defaultManager] createDirectoryAtPath:path withIntermediateDirectories:YES attributes:nil error:nil];
    }
    return path;
}

+ (NSString *)documentTempPath
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *path = [paths objectAtIndex:0];
    path = [path stringByAppendingPathComponent:SMTempDirectoryName];
    if (![[self defaultManager] fileExistsAtPath:path]) {
        [[self defaultManager] createDirectoryAtPath:path withIntermediateDirectories:YES attributes:nil error:nil];
    }
    return path;
}

+ (NSString *)tempBundlePathForName:(NSString *)name
{
    NSString *basePath = [self documentTempPath]; // NSTemporaryDirectory()
    NSString *bundlePath = [[basePath stringByAppendingPathComponent:name] stringByAppendingString:@".zip"];
    return bundlePath;
}

@end
