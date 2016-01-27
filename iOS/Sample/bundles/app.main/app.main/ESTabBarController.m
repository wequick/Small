//
//  ESTabBarController.m
//  app.main
//
//  Created by galen on 16/1/25.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "ESTabBarController.h"

#import "Small/Small.h"

@implementation ESTabBarController

- (instancetype)init {
    if (self = [super init]) {
        [self initTabs];
    }
    return self;
}

- (void)initTabs {
    NSArray *items = @[@{@"id": @"home", @"title":@"Home", @"type": @(UITabBarSystemItemFeatured)},
                       @{@"id": @"mine", @"title":@"Mine", @"type": @(UITabBarSystemItemContacts)}];
    NSMutableArray *controllers = [NSMutableArray arrayWithCapacity:items.count];
    NSMutableArray *barItems = [NSMutableArray arrayWithCapacity:items.count];
    for (NSDictionary *item in items) {
        NSString *uri = item[@"id"];
        UIViewController *controller = [Small controllerForUri:uri];
        UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:controller];
        
        UITabBarSystemItem type = [item[@"type"] intValue];
        UITabBarItem *barItem = [[UITabBarItem alloc] initWithTabBarSystemItem:type tag:0];
        [barItem setTitle:item[@"title"]];
        
        [controllers addObject:nav];
        [barItems addObject:barItem];
    }
    
    for (int i = 0; i < [controllers count]; i++) {
        UINavigationController *nav = [controllers objectAtIndex:i];
        UIViewController *vc = [nav topViewController];
        UITabBarItem *item = [barItems objectAtIndex:i];
        vc.tabBarItem = item;
    }
    self.viewControllers = controllers;
}

@end
