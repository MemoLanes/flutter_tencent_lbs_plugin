//
//  TencentLBSLocation.h
//  TencentLBS
//
//  Created by mirantslu on 16/4/19.
//  Copyright © 2016年 Tencent. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

NS_ASSUME_NONNULL_BEGIN

/** @brief 调试模式开关，0-关闭，1-开启 */
#define TENCENTLBS_DEBUG 0

/** DR 定位结果来源枚举 */
typedef NS_ENUM(NSInteger, TencentLBSDRProvider) {
    TencentLBSDRProviderError      = -2,       ///< 错误，可能未开启 DR
    TencentLBSDRProviderUnkown     = -1,       ///< 定位结果来源未知
    TencentLBSDRProviderFusion     = 0,        ///< 定位结果来源融合的结果
    TencentLBSDRProviderGPS        = 1,        ///< 定位结果来源 GPS
    TencentLBSDRProviderNetWork    = 2,        ///< 定位结果来源网络
};

/** 定位作弊码枚举，使用位掩码标识多种作弊类型 */
typedef NS_OPTIONS(NSUInteger, TencentLBSLocationFake) {
    TencentLBSLocationFakeOK                    = 0,        ///< 正常
    TencentLBSLocationFakeFirstCallbackSpeed    = 1 << 6,   ///< 64，系统首次定位回调速度校验
    TencentLBSLocationFakeSimulation            = 1 << 7,   ///< 128，模拟位置校验
    TencentLBSLocationFakeLocationAge           = 1 << 8,   ///< 256，系统当前时间与 Location 的时间校验
    TencentLBSLocationFakeLocationSame          = 1 << 9,   ///< 512，系统一直回调同一个点校验
};

/** 普适定位结果来源枚举 */
typedef NS_ENUM(NSInteger, TencentLBSLocationProvider) {
    TencentLBSLocationProviderUnkown            = -1,       ///< 普适定位结果来源未知
    TencentLBSLocationProviderGPS               = 0,        ///< 普适定位结果来源手机的 GPS
    TencentLBSLocationProviderNetWork           = 1,        ///< 普适定位结果来源手机的 Network
    TencentLBSLocationProviderSimulated         = 2,        ///< 普适定位结果来源模拟定位
    TencentLBSLocationProviderAccessoryGPS      = 3,        ///< 普适定位结果来源外设的 GPS
    TencentLBSLocationProviderAccessoryNetwork  = 4,        ///< 普适定位结果来源外设的 Network
};

/**
 POI 兴趣点信息模型，包含 POI 的基本属性信息。
 用于封装定位结果周边的兴趣点数据。
 */
@interface TencentLBSPoi : NSObject<NSSecureCoding, NSCopying>

/** @brief 当前 POI 的 uid */
@property (nonatomic, copy) NSString *uid;
/** @brief 当前 POI 的名称 */
@property (nonatomic, copy) NSString *name;
/** @brief 当前 POI 的地址 */
@property (nonatomic, copy) NSString *address;
/** @brief 当前 POI 的类别 */
@property (nonatomic, copy) NSString *catalog;
/** @brief 当前 POI 的经度 */
@property (nonatomic, assign) double longitude;
/** @brief 当前 POI 的纬度 */
@property (nonatomic, assign) double latitude;
/** @brief 当前 POI 与当前位置的距离 */
@property (nonatomic, assign) double distance;

@end

/**
 定位结果信息模型，封装定位返回的位置信息及逆地理编码信息。
 包含经纬度、行政区划、POI 等完整的位置描述数据。
 */
@interface TencentLBSLocation : NSObject<NSSecureCoding, NSCopying>

/** @brief 当前位置的 CLLocation 信息 */
@property (nonatomic, strong) CLLocation *location;

/** @brief 当前位置的行政区划，0-表示中国大陆、港、澳，1-表示其他 */
@property (nonatomic, assign) NSInteger areaStat;

/** @brief 当前位置的作弊码 */
@property (nonatomic, assign) TencentLBSLocationFake fakeCode;

/** @brief 室内定位楼宇 ID */
@property (nonatomic, copy, nullable) NSString *buildingId;

/** @brief 室内定位楼层 */
@property (nonatomic, copy, nullable) NSString *buildingFloor;

/** @brief 室内定位类型，0 表示普通定位结果，1 表示蓝牙室内定位结果 */
@property (nonatomic, assign) NSInteger indoorLocationType;

/** @brief DR 定位结果来源 */
@property (nonatomic, assign) TencentLBSDRProvider drProvider;

/** @brief 普适定位结果来源 */
@property (nonatomic, assign) TencentLBSLocationProvider locationProvider;

/**
 当前位置的名称
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelName 或 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *name;

/**
 当前位置的地址
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelName 或 TencentLBSRequestLevelAdminName 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *address;

/**
 国家编码，例如中国为 156
 @warning 该接口涉及到 WebService API，请参考 https://lbs.qq.com/service/webService/webServiceGuide/webServiceOverview 中的配额限制说明，并将申请的有效 key 通过 TencentLBSLocationManager 的 setDataWithValue:forKey: 方法设置，其中 key 为固定值 @"ReGeoCodingnKey"，否则将返回默认值 0
 */
@property (nonatomic, assign) NSInteger nationCode;

/**
 当前位置的城市编码
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *code;

/**
 当前位置的国家
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *nation;

/**
 当前位置的省份
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *province;

/**
 当前位置的城市固话编码
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *cityPhoneCode;

/**
 当前位置的城市
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *city;

/**
 当前位置的区县
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *district;

/**
 当前位置的乡镇
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *town;

/**
 当前位置的村
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *village;

/**
 当前位置的街道
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *street;

/**
 当前位置的街道编码
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelAdminName 或 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, copy, nullable) NSString *street_no;

/**
 当前位置周围的 POI 列表
 @warning 仅当 TencentLBSRequestLevel 为 TencentLBSRequestLevelPoi 有返回值，否则为空
 */
@property (nonatomic, strong, nullable) NSArray<TencentLBSPoi*> *poiList;

/**
 计算两个位置之间的横向距离
 @param location 目标位置
 @return 两个位置之间的距离，单位为米
 */
- (double)distanceFromLocation:(const TencentLBSLocation *)location;

// 测试使用
#if TENCENTLBS_DEBUG
@property (nonatomic, copy, nullable) NSString *halleyTime;
#endif

@end

NS_ASSUME_NONNULL_END
