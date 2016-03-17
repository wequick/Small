//
//  UIUtils.h
//  lib.utils
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@interface UIUtils : NSObject

+ (void)alert:(NSString *)message atController:(UIViewController *)controller;

+ (void)hud:(NSString *)message atController:(UIViewController *)controller;
+ (void)updateHud:(NSString *)message;
+ (void)hideHud;

+ (void)toast:(NSString *)message inSeconds:(NSTimeInterval)seconds atController:(UIViewController *)controller;
+ (void)shortToast:(NSString *)message atController:(UIViewController *)controller; // 1 seconds
+ (void)longToast:(NSString *)message atController:(UIViewController *)controller; // 3 seconds

@end
