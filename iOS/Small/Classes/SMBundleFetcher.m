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

#import "SMBundleFetcher.h"
#import "SMMacro.h"
#import "ZipArchive.h"
#import "SMFileManager.h"

//______________________________________________________________________________
// SMBundleURLConnection

@interface SMBundleURLConnection : NSURLConnection

@property (nonatomic, strong) NSString *bundleName;
@property (nonatomic, strong) NSMutableData *bundleData;
@property (nonatomic, assign) long long bundleBytes;
@property (nonatomic, strong) void (^receivedHandler)(long long receivedBytes, long long totalBytes);
@property (nonatomic, strong) void (^complectionHandler)(NSString *path, NSError *error);

@property (nonatomic, strong) NSDate *startDate;

@end

@implementation SMBundleURLConnection

// Nothing

@end

//______________________________________________________________________________
// SMBundleFetcher

@implementation SMBundleFetcher

DEF_SINGLETON(defaultFetcher)

- (void)fetchBundleOfName:(NSString *)name
                  withURL:(NSURL *)url
                 received:(void (^)(long long receivedBytes, long long totalBytes))received
               completion:(void (^)(NSString *path, NSError *error))complection
{
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url cachePolicy:NSURLRequestReloadIgnoringCacheData timeoutInterval:30];
    [request addValue:@"gzip" forHTTPHeaderField:@"Accept-Encoding"];
    SMBundleURLConnection *connection = [[SMBundleURLConnection alloc] initWithRequest:request delegate:self];
    connection.bundleName = name;
    connection.receivedHandler = received;
    connection.complectionHandler = complection;
    connection.startDate = [NSDate date];
    [connection start];
}

- (void)connection:(SMBundleURLConnection *)connection didReceiveResponse:(NSURLResponse *)response {
    connection.bundleBytes = [response expectedContentLength];
}

- (void)connection:(SMBundleURLConnection *)connection didReceiveData:(NSData *)data {
    if (connection.bundleData == nil) {
        connection.bundleData = [[NSMutableData alloc] initWithData:data];
    } else {
        [connection.bundleData appendData:data];
    }
    if (connection.receivedHandler != nil) {
        connection.receivedHandler([data length], connection.bundleBytes);
    }
}

- (void)connection:(SMBundleURLConnection *)connection didFailWithError:(NSError *)error {
    // TODO: Breakpoint Continuingly
    // ...
    if (connection.complectionHandler != nil) {
        connection.complectionHandler(nil, error);
        connection.complectionHandler = nil;
    }
    connection.receivedHandler = nil;
    connection.bundleData = nil;
}

- (void)connectionDidFinishLoading:(SMBundleURLConnection *)connection {
    NSDate *date = [NSDate date];
    NSLog(@"@@ fetch bundle[%@] with duration %.2lfs", connection.bundleName, [date timeIntervalSinceDate:connection.startDate]);
    connection.startDate = date;
    NSString *name = connection.bundleName;
    NSString *zipPath = [SMFileManager tempBundlePathForName:name];
    // Delete old bundle
    if ([[NSFileManager defaultManager] fileExistsAtPath:zipPath]) {
        [[NSFileManager defaultManager] removeItemAtPath:zipPath error:nil];
    }
    // Save new bundle
    [connection.bundleData writeToFile:zipPath atomically:YES];
    if (connection.complectionHandler != nil) {
        connection.complectionHandler(zipPath, nil);
        connection.complectionHandler = nil;
    }
    connection.receivedHandler = nil;
    connection.bundleData = nil;
}

@end
