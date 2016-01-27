//
//  ESDetailController.m
//  app.detail
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "ESDetailController.h"

@interface ESDetailController ()

@end

@implementation ESDetailController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view from its nib.
    self.title = @"Detail";
    self.fromLabel.text = self.from;
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

@end
