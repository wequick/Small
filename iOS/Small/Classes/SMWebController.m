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

#import "SMWebController.h"
#import "Small.h"
#import "SMWebView.h"
#import "_SMWebNavigationItem.h"

@interface SMWebController ()
{
    NSTimer *_readTitleTimer;
    struct {
        unsigned int injected:1;
    } _smFlags;
}

@end

NSString *const SMWebViewDidStartLoadNotification = @"SMWebViewDidStartLoadNotification";
NSString *const SMWebViewDidFinishLoadNotification = @"SMWebViewDidFinishLoadNotification";
NSString *const SMWebViewErrorKey = @"SMWebViewError";

static NSString *const kSmallInjectJS = @
// Avoid function debug
"Function.prototype.toString=function(s){return ''};"
// Avoid long press to show popup menu
"document.body.style.webkitTouchCallout='none';"
// Window close listener
"window._onclose=function(){"
    "if(typeof(onbeforeclose)=='function'){"
        "var s=onbeforeclose();"
        "if(typeof(s)=='string'&&!confirm(s))"
            "return false;"
    "}"
    "if(typeof(onclose)=='function')"
        "return onclose();"
"};"
"window._close=function(ret){"
    "Small.invoke('pop',{ret:ret});"
"};"
"window.close=function(){"
    "var ret=_onclose();"
    "if(ret==false)return;"
    "_close(ret);"
"};";

static NSString *const kSmallGetMetasJS = @
"var ms=document.head.getElementsByTagName('meta');"
"var _ms={};"
"for (var i=0;i<ms.length;i++) {"
    "var m=ms[i];"
    "if(m.name)"
        "_ms[m.name]=m.content;"
"};JSON.stringify(_ms)";

@implementation SMWebController

- (instancetype)init {
    if (self = [super init]) {
        
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.view = self.webView;
    NSArray *controllers = [self.navigationController viewControllers];
    if ([controllers count] > 1) {
        // Customize backBarButtonItem
        [self sm_setBackBarButtonItemWithTitle:nil target:self action:@selector(backButtonClick:)];
    }
    
    _readTitleTimer = [NSTimer scheduledTimerWithTimeInterval:.05 target:self selector:@selector(readTitleTimerTick:) userInfo:nil repeats:YES];
    
//    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemRefresh target:self action:@selector(refreshItemClick:)];
}

//- (void)refreshItemClick:(id)sender {
//    _smFlags.injected = NO;
//    [self.webView reload];
//}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (void)readTitleTimerTick:(id)sender {
    if (self.title == nil || [self.title isEqualToString:@""]) {
        self.title = [self.webView stringByEvaluatingJavaScriptFromString:@"document.title"];
    } else {
        if (_readTitleTimer != nil) {
            [_readTitleTimer invalidate];
            _readTitleTimer = nil;
        }
    }
}

- (void)dealloc {
    if (_readTitleTimer != nil) {
        [_readTitleTimer invalidate];
        _readTitleTimer = nil;
    }
}

- (void)injectJsForWebView:(UIWebView *)webView {
    if (_smFlags.injected) {
        return;
    }
    [webView stringByEvaluatingJavaScriptFromString:kSmallInjectJS];
    JSContext *context = [webView valueForKeyPath:@"documentView.webView.mainFrame.javaScriptContext"];
    context[@"Small"] = self;
    
    _smFlags.injected = YES;
}

- (void)invoke:(NSString *)method parameters:(NSDictionary *)parameters callback:(JSValue *)callback {
    // WebThread
    dispatch_async(dispatch_get_main_queue(), ^{
        // UIThread
        if ([method isEqualToString:@"confirm"]) {
            [(SMWebView *)self.webView confirm:parameters[@"message"]
                                     withTitle:parameters[@"title"]
                             cancelButtonTitle:parameters[@"cancel"]
                             otherButtonTitles:parameters[@"buttons"]
                                   complection:^(NSInteger index) {
                                       [callback callWithArguments:@[@(index)]];
             }];
        } else if ([method isEqualToString:@"alert"]) {
            [(SMWebView *)self.webView alert:parameters[@"message"]
                                   withTitle:parameters[@"title"]
                          confirmButtonTitle:parameters[@"ok"]
                                 complection:^(NSInteger index) {
                 
             }];
        } else if ([method isEqualToString:@"pop"]) {
            [self popWithWebResult:parameters[@"ret"]];
        } else {
            void (^jsHandler)(NSDictionary *, JSValue *) = [Small jsHandlerForMethod:method];
            if (jsHandler != nil) {
                jsHandler(parameters, callback);
            }
        }
    });
}

//___________________________________________________________________________________________________

- (BOOL)webView:(SMWebView *)webView shouldStartLoadWithRequest:(NSURLRequest *)request navigationType:(UIWebViewNavigationType)navigationType
{
    NSURL *url = [request URL];
    
    // If the link is an anchor reference, scroll to the anchor
    NSString *anchor = [url fragment];
    if (anchor != nil) {
        NSString *result = [webView stringByEvaluatingJavaScriptFromString:[NSString stringWithFormat:@"var y=document.body.scrollTop;var e=document.getElementsByName('%@')[0];while(e){y+=e.offsetTop-e.scrollTop+e.clientTop;e=e.offsetParent;}y", anchor]];
        float anchorY = [result floatValue];
        CGPoint offset = [webView.scrollView contentOffset];
        offset.y = anchorY - [webView.scrollView contentInset].top;
        [UIView animateWithDuration:.3 animations:^{
            [webView.scrollView setContentOffset:offset];
        }];
        return YES;
    }
    
    switch (navigationType) {
        case UIWebViewNavigationTypeOther: {
            // If the web view is never hit which means this should be a url redirect by web server or javascript.
            if (!webView.isHit) return YES;
            if ([webView.originalRequest.URL isEqual:request.URL]) return YES;
            // Open a new page while href changed by click a link or execute javascript `location.href=xx'
            [Small openURL:url fromView:webView];
            return NO;
        }
        case UIWebViewNavigationTypeFormSubmitted:
            if ([[request HTTPMethod] isEqualToString:@"POST"]) {
                // Open new window with the post request
                UIViewController *controller = (id)[webView nextResponder];
                SMWebController *webController = [[SMWebController alloc] init];
                webController.webView = [[SMWebView alloc] initWithFrame:[UIScreen mainScreen].bounds];
                [webController.webView loadRequest:request];
                [controller.navigationController pushViewController:webController animated:YES];
                return NO;
            }
        case UIWebViewNavigationTypeLinkClicked:
            // Open the link with {Small}
            if ([self.delegate respondsToSelector:@selector(webViewControllerShouldLoadURL:)]) {
                if (![self.delegate webViewControllerShouldLoadURL:url]) {
                    return NO;
                }
            }
            [Small openURL:url fromView:webView];
            return NO;
        case UIWebViewNavigationTypeReload:
            _smFlags.injected = NO;
            break;
        default:
            break;
    }
    
    return YES;
}

- (void)webViewDidStartLoad:(UIWebView *)webView {
    [[NSNotificationCenter defaultCenter] postNotificationName:SMWebViewDidStartLoadNotification object:webView];
}

- (void)webViewDidFinishLoad:(UIWebView *)webView
{
    if (self.title == nil || [self.title isEqualToString:@""]) {
        self.title = [webView stringByEvaluatingJavaScriptFromString:@"document.title"];
    }
    
    [self injectJsForWebView:webView];
    if (_readTitleTimer) {
        [_readTitleTimer invalidate];
        _readTitleTimer = nil;
    }
    
    /* Parse meta data for navigation bar items
     * e.g. <meta name="right-bar-item" content="type=share;onclick=rightBar()>
     */
    NSString *metasJson = [webView stringByEvaluatingJavaScriptFromString:kSmallGetMetasJS];
    NSDictionary *metas = [NSJSONSerialization JSONObjectWithData:[metasJson dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableLeaves error:nil];
    if (metas != nil) {
        [self initNavigationBarItemsByMetas:metas];
    }
    
    [[NSNotificationCenter defaultCenter] postNotificationName:SMWebViewDidFinishLoadNotification object:webView];
}

- (void)webView:(UIWebView *)webView didFailLoadWithError:(NSError *)error {
    [[NSNotificationCenter defaultCenter] postNotificationName:SMWebViewDidFinishLoadNotification object:webView userInfo:@{SMWebViewErrorKey:error}];
}

#pragma mark - Navigation bar

- (void)backButtonClick:(id)sender {
    NSString *ret = [self.webView stringByEvaluatingJavaScriptFromString:@"window._onclose()"];
    if ([ret isEqual:[NSNull null]] || [ret isEqualToString:@"false"]) {
        return;
    }
    [self popWithWebResult:ret];
}

- (void)popWithWebResult:(NSString *)result {
    if (self.delegate != nil) {
        [self.delegate webViewControllerDidPopWithWebResult:result];
    }
    [self.navigationController popViewControllerAnimated:YES];
}

- (void)initNavigationBarItemsByMetas:(NSDictionary *)metas
{
    // Collect bar items
    //  "*-bar-item":"content"
    NSMutableDictionary *metaContents = [[NSMutableDictionary alloc] init];
    for (NSString *name in metas) {
        NSRange range = [name rangeOfString:@"-bar-item"];
        if (range.length == 0) {
            continue;
        }
        
        NSString *content = metas[name];
        NSArray *attrs = [content componentsSeparatedByString:@","];
        NSMutableDictionary *dict = [[NSMutableDictionary alloc] init];
        for (NSString *attr in attrs) {
            NSInteger index = [attr rangeOfString:@"="].location;
            NSString *key, *value;
            if (index == NSNotFound) {
                key = @"title"; // Default to title
                value = attr;
            } else {
                key = [attr substringToIndex:index];
                value = [attr substringFromIndex:index + 1];
            }
            dict[key] = value;
        }
        
        NSString *pos = [name substringToIndex:range.location];
        metaContents[pos] = dict;
    }
    
    // Init left bar button item
    NSDictionary *metaContent = metaContents[@"left"];
    if (metaContent != nil) {
        NSMutableArray *leftItems = [NSMutableArray arrayWithArray:self.navigationItem.leftBarButtonItems];
        if ([leftItems count] == 1) {
            UIBarButtonItem *metaItem = [[_SMWebNavigationItem alloc] initWithMetaContent:metaContent target:self action:@selector(metaBarButtonItemClick:)];
            [leftItems addObject:metaItem];
            self.navigationItem.leftBarButtonItems = leftItems;
        }
    }
    
    // Init title item
    metaContent = metaContents[@"center"];
    if (metaContent != nil) {
        NSString *title = metaContent[@"title"];
        self.title = title;
        // TODO: add title click event
    }
    
    // Init right bar button item
    metaContent = metaContents[@"right"];
    if (metaContent != nil) {
        NSMutableArray *rightItems = [NSMutableArray arrayWithArray:self.navigationItem.rightBarButtonItems];
        if ([rightItems count] == 0) {
            UIBarButtonItem *metaItem = [[_SMWebNavigationItem alloc] initWithMetaContent:metaContent target:self action:@selector(metaBarButtonItemClick:)];
            [rightItems addObject:metaItem];
            self.navigationItem.rightBarButtonItems = rightItems;
        }
    }
    
    // TODO: Menu bar button item
    // @"···"
}

- (void)metaBarButtonItemClick:(_SMWebNavigationItem *)sender {
    if ([sender.onclick length] > 0) {
        [self.webView stringByEvaluatingJavaScriptFromString:sender.onclick];
    }
}

@end
