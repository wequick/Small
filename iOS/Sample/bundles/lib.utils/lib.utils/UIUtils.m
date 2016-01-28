//
//  UIUtils.m
//  lib.utils
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "UIUtils.h"

@implementation UIUtils

static UIAlertController *kHud;
static UIAlertController *kToast;

+ (void)alert:(NSString *)message atController:(UIViewController *)controller {
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"Small" message:[NSString stringWithFormat:@"lib.utils: %@", message] preferredStyle:UIAlertControllerStyleAlert];
    [alertController addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:nil]];
    [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [controller presentViewController:alertController animated:YES completion:nil];
}

+ (void)hud:(NSString *)message atController:(UIViewController *)controller
{
    kHud = [UIAlertController alertControllerWithTitle:nil message:message preferredStyle:UIAlertControllerStyleAlert];
    [controller presentViewController:kHud animated:YES completion:nil];
}

+ (void)updateHud:(NSString *)message {
    if (kHud == nil) return;
    [kHud setMessage:message];
}

+ (void)hideHud {
    if (kHud == nil) return;
    
    [kHud dismissViewControllerAnimated:NO completion:^{
        kHud = nil;
    }];
}

+ (void)toast:(NSString *)message inSeconds:(NSTimeInterval)seconds atController:(UIViewController *)controller
{
    kToast = [UIAlertController alertControllerWithTitle:nil message:[NSString stringWithFormat:@"lib.utils: %@", message] preferredStyle:UIAlertControllerStyleActionSheet];
    [controller presentViewController:kToast animated:YES completion:^{
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(seconds * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [kToast dismissViewControllerAnimated:NO completion:^{
                kToast = nil;
            }];
        });
    }];
}

+ (void)shortToast:(NSString *)message atController:(UIViewController *)controller {
    [self toast:message inSeconds:1 atController:controller];
}

+ (void)longToast:(NSString *)message atController:(UIViewController *)controller {
    [self toast:message inSeconds:3 atController:controller];
}

@end
