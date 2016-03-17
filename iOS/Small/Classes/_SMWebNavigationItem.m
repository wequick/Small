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

#import "_SMWebNavigationItem.h"

@implementation _SMWebNavigationItem

- (instancetype)initWithMetaContent:(NSDictionary *)meta target:(id)target action:(SEL)action
{
    NSString *type = meta[@"type"];
    NSString *title = meta[@"title"];
    
    if (type != nil) {
        UIImage *image = nil; // TODO: get user-defined image by type
        if (image != nil) {
            self = [super initWithImage:image style:UIBarButtonItemStylePlain target:target action:action];
        } else {
            UIBarButtonSystemItem item = UIBarButtonSystemItemDone;
            if ([type isEqualToString:@"done"]) { item = UIBarButtonSystemItemDone; }
            else if ([type isEqualToString:@"cancel"]) { item = UIBarButtonSystemItemCancel; }
            else if ([type isEqualToString:@"edit"]) { item = UIBarButtonSystemItemEdit; }
            else if ([type isEqualToString:@"save"]) { item = UIBarButtonSystemItemSave; }
            else if ([type isEqualToString:@"add"]) { item = UIBarButtonSystemItemAdd; }
            else if ([type isEqualToString:@"compose"]) { item = UIBarButtonSystemItemCompose; }
            else if ([type isEqualToString:@"reply"]) { item = UIBarButtonSystemItemReply; }
            else if ([type isEqualToString:@"action"]) { item = UIBarButtonSystemItemAction; }
            else if ([type isEqualToString:@"share"]) { item = UIBarButtonSystemItemAction; } // share as action
            else if ([type isEqualToString:@"organize"]) { item = UIBarButtonSystemItemOrganize; }
            else if ([type isEqualToString:@"bookmarks"]) { item = UIBarButtonSystemItemBookmarks; }
            else if ([type isEqualToString:@"search"]) { item = UIBarButtonSystemItemSearch; }
            else if ([type isEqualToString:@"refresh"]) { item = UIBarButtonSystemItemRefresh; }
            else if ([type isEqualToString:@"stop"]) { item = UIBarButtonSystemItemStop; }
            else if ([type isEqualToString:@"camera"]) { item = UIBarButtonSystemItemCamera; }
            else if ([type isEqualToString:@"trash"]) { item = UIBarButtonSystemItemTrash; }
            else if ([type isEqualToString:@"play"]) { item = UIBarButtonSystemItemPlay; }
            else if ([type isEqualToString:@"pause"]) { item = UIBarButtonSystemItemPause; }
            else if ([type isEqualToString:@"rewind"]) { item = UIBarButtonSystemItemRewind; }
            else if ([type isEqualToString:@"fastForward"]) { item = UIBarButtonSystemItemFastForward; }
            else if ([type isEqualToString:@"undo"]) { item = UIBarButtonSystemItemUndo; }
            else if ([type isEqualToString:@"redo"]) { item = UIBarButtonSystemItemRedo; }
            else if ([type isEqualToString:@"pageCurl"]) { item = UIBarButtonSystemItemPageCurl; }
            
            self = [super initWithBarButtonSystemItem:item target:target action:action];
        }
    } else {
        self = [super initWithTitle:title style:UIBarButtonItemStylePlain target:target action:action];
    }
    
    if (self != nil) {
        self.onclick = meta[@"onclick"];
    }
    return self;
}

@end
