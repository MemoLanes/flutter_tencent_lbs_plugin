//
//  TencentLBSLocationUtils.h
//  TencentLBS
//
//  Created by mirantslu on 16/8/11.
//  Copyright © 2016年 Tencent. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

@class TencentLBSLocation;

NS_ASSUME_NONNULL_BEGIN

/**
 定位工具类，提供距离计算、坐标转换、区域判断等实用方法。
 */
@interface TencentLBSLocationUtils : NSObject

/**
 计算两个坐标点的距离
 @param coordinate 第一个坐标点
 @param coordinate2 第二个坐标点
 @return 两点之间的距离，单位为米
 */
+ (double)distanceBetweenTwoCoordinate2D:(const CLLocationCoordinate2D *)coordinate coordinateTwo:(const CLLocationCoordinate2D *)coordinate2;

/**
 计算两个 CLLocation 的距离
 @param location 第一个位置
 @param location2 第二个位置
 @return 两个位置之间的距离，单位为米
 */
+ (double)distanceBetweenTwoCLLocations:(const CLLocation *)location locationTwo:(const CLLocation *)location2;

/**
 计算两个 TencentLBSLocation 的距离
 @param location 第一个位置
 @param location2 第二个位置
 @return 两个位置之间的距离，单位为米
 */
+ (double)distanceBetweenTwoTencentLBSLocations:(const TencentLBSLocation *)location locationTwo:(const TencentLBSLocation *)location2;

/**
 判断经纬度是否在国内
 @param latitude 纬度
 @param longitude 经度
 @return 在国内返回 YES，否则返回 NO
 */
+ (BOOL)isInRegionWithLatitude:(double)latitude longitude:(double)longitude;

/**
 WGS84 坐标转换为 GCJ02 坐标
 @param coordinate WGS84 坐标
 @return 转换后的 GCJ02 坐标
 */
+ (CLLocationCoordinate2D)WGS84TOGCJ02:(CLLocationCoordinate2D)coordinate;

@end

/**
 定位服务管理类，提供设备 ID 设置等功能，用于发布前联调使用。
 */
@interface TencentLBSServiceManager : NSObject

/** @brief 设备 ID，如 QQ 号、微信号或其他登录账号，可用在发布前联调使用 */
@property (atomic, copy) NSString *deviceID;

/** @brief 获取单例实例 */
+ (instancetype)sharedInsance;

@end

NS_ASSUME_NONNULL_END
