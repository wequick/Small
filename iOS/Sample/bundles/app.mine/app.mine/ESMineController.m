//
//  ESMineController.m
//  app.mine
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "ESMineController.h"
#import "Small/Small.h"

@interface ESMineController ()

@end

@implementation ESMineController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"Mine";
    self.tabBarItem.title = @"Mine";
    // Do any additional setup after loading the view from its nib.
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (IBAction)issueClick:(id)sender {
    [Small openUri:@"https://github.com/wequick/Small/issues" fromController:self];
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
