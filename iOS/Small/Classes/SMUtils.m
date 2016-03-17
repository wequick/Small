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

#import "SMUtils.h"

@implementation SMUtils

+ (NSDictionary *)querysWithString:(NSString *)query
{
    NSMutableDictionary *querys = nil;
    NSArray *components = [query componentsSeparatedByString:@"&"];
    for (NSString *component in components) {
        NSRange range = [component rangeOfString:@"="];
        if (range.length == 0) {
            continue;
        }
        
        NSString *key = [component substringToIndex:range.location];
        NSString *value = [component substringFromIndex:range.location + 1];
        
        if (querys == nil) {
            querys = [[NSMutableDictionary alloc] init];
        }
        [querys setObject:value forKey:key];
    }
    return querys;
}

+ (BOOL)isBundleScheme:(NSString *)scheme
{
    return ([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"] || [scheme isEqualToString:@"file"]);
}

@end
