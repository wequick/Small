//
//  ViewController.m
//  DevSample
//
//  Created by galen on 16/1/22.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "ViewController.h"

#import <Small/Small.h>

@interface ViewController ()

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view, typically from a nib.    
    [Small setBaseUri:@"http://m.wequick.net/demo/"];
    [Small setUpWithComplection:^{
        UIViewController *mainController = [Small controllerForUri:@"main"];
        [self presentViewController:mainController animated:NO completion:nil];
    }];
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

@end
