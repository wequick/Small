
//
//  ESHomeController.m
//  app.home
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import "ESHomeController.h"
#import "Small/Small.h"
#import "net_wequick_example_small_lib_utils/lib.utils.h"

@interface ESHomeController ()

@end

@implementation ESHomeController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"Small Sample";
    self.tabBarItem.title = @"Home";
    
    // Do any additional setup after loading the view from its nib.
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (IBAction)detailClick:(id)sender {
    [Small openUri:@"detail?from=app.home" fromController:self];
}

- (IBAction)aboutClick:(id)sender {
    [Small openUri:@"about" fromController:self];
}

- (IBAction)utilsClick:(id)sender {
    [UIUtils shortToast:@"Hello World!" atController:self];
}

- (IBAction)upgradeClick:(id)sender {
    [self checkUpgrade];
}

- (void)checkUpgrade {
    [UIUtils hud:@"Checking for updates..." atController:self];
    [self requestUpgradeInfo:[Small bundleVersions] complection:^(NSDictionary *upgradeInfo) {
        [UIUtils updateHud:@"Upgrading..."];
        SMBundle *bundle = [SMBundle bundleForName:upgradeInfo[@"id"]];
        [self upgradeBundle:bundle withUrlString:upgradeInfo[@"url"] complection:^(NSError *error) {
            if (error != nil) {
                [UIUtils updateHud:[NSString stringWithFormat:@"Upgrade failed: %@", error.localizedDescription]];
            } else {
                [UIUtils updateHud:@"Upgrade success! Restart to see changes!"];
            }
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [UIUtils hideHud];
            });
        }];
    }];
}

- (void)requestUpgradeInfo:(NSDictionary *)bundleVersion complection:(void (^)(NSDictionary *))complection {
    // Place your http request here.
    NSDictionary *upgradeInfo = @{@"id": @"net.wequick.example.small.app.home",
                                  @"url": @"http://code.wequick.net/small/upgrade/net_wequick_example_small_app_home.framework.zip"};
    complection(upgradeInfo);
}

- (void)upgradeBundle:(SMBundle *)bundle withUrlString:(NSString *)urlString complection:(void (^)(NSError *error))complection {
    NSURL *url = [NSURL URLWithString:urlString];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url cachePolicy:NSURLRequestReloadIgnoringCacheData timeoutInterval:30];
    [request addValue:@"gzip" forHTTPHeaderField:@"Accept-Encoding"];
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSURLResponse *response;
        NSError *error = nil;
        NSData *data = [NSURLConnection sendSynchronousRequest:request returningResponse:&response error:&error];
        if (error == nil) {
            do {
                if (![data writeToFile:bundle.patchFile atomically:NO]) {
                    error = [NSError errorWithDomain:@"UpgradeDomain" code:1 userInfo:@{NSLocalizedDescriptionKey: @"Failed to save download plugin!"}];
                    break;
                }
                // While patch file is ready, call this.
                if (![bundle upgrade]) {
                    error = [NSError errorWithDomain:@"UpgradeDomain" code:2 userInfo:@{NSLocalizedDescriptionKey: @"Failed to upgrade bundle!"}];
                }
            } while (NO);
        }
        if (error != nil) {
            complection(error);
            return;
        }
        NSLog(@"download patch: %@", bundle.patchFile);
        dispatch_sync(dispatch_get_main_queue(), ^{
            complection(error);
        });
    });
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
