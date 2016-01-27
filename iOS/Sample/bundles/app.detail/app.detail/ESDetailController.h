//
//  ESDetailController.h
//  app.detail
//
//  Created by galen on 16/1/26.
//  Copyright © 2016年 galen. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface ESDetailController : UIViewController

@property (nonatomic, strong) NSString *from; // for uri

@property (weak, nonatomic) IBOutlet UILabel *fromLabel;

@end
