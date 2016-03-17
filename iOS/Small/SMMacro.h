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

#define AS_SINGLETON(_name_)            _AS_SINGLETON(instancetype, _name_)
#define DEF_SINGLETON(_name_)           _DEF_SINGLETON(instancetype, self, _name_)

#define AS_SINGLETON2(_class_, _name_)  _AS_SINGLETON(_class_ *, _name_)
#define DEF_SINGLETON2(_class_, _name_) _DEF_SINGLETON(_class_ *, _class_, _name_)

#define _AS_SINGLETON(_type_, _name_)  \
+ (_type_)_name_;

#define _DEF_SINGLETON(_type_, _class_, _name_) \
+ (_type_)_name_ { \
static id o = nil; \
static dispatch_once_t onceToken; \
dispatch_once(&onceToken, ^{ \
o = [[_class_ alloc] init]; \
}); \
return o; \
}

FOUNDATION_STATIC_INLINE
BOOL SMStringHasPrefix(NSString *fullString, NSString *prefixString)
{
    NSRange range = [fullString rangeOfString:prefixString];
    if (range.location == 0 && range.length == prefixString.length) {
        return YES;
    }
    return NO;
}

FOUNDATION_STATIC_INLINE
BOOL SMStringHasSuffix(NSString *fullString, NSString *suffixString)
{
    NSRange range = [fullString rangeOfString:suffixString];
    if (range.location == fullString.length - suffixString.length && range.length == suffixString.length) {
        return YES;
    }
    return NO;
}
