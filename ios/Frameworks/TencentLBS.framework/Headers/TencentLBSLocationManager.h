//
//  TencentLBSLocationManager.h
//  TencentLBS
//
//  Created by mirantslu on 16/4/19.
//  Copyright © 2016年 Tencent. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import "TencentLBSLocation.h"

NS_ASSUME_NONNULL_BEGIN

/** 定位请求级别枚举，决定返回的位置信息详细程度 */
typedef NS_ENUM(NSUInteger, TencentLBSRequestLevel) {
    TencentLBSRequestLevelGeo       = 0,    ///< latitude, longitude, altitude, accuracy
    TencentLBSRequestLevelName      = 1,    ///< latitude, longitude, altitude, accuracy, name, address
    TencentLBSRequestLevelAdminName = 3,    ///< latitude, longitude, altitude, accuracy, name, address, nation, province, city, district, town, village, street, streetNo
    TencentLBSRequestLevelPoi       = 4,    ///< latitude, longitude, altitude, accuracy, nation, province, city, district, town, village, street, streetNo, poiList，没有 name 和 address
};

/** 定位坐标系类型枚举 */
typedef NS_ENUM(NSUInteger, TencentLBSLocationCoordinateType) {
    TencentLBSLocationCoordinateTypeGCJ02 = 0,      ///< 火星坐标，即国测局坐标
    TencentLBSLocationCoordinateTypeWGS84 = 1,      ///< 地球坐标，注：如果是海外，无论设置的是火星坐标还是地球坐标，返回的都是地球坐标
};

/** 定位错误码枚举 */
typedef NS_ENUM(NSUInteger, TencentLBSLocationError) {
    TencentLBSLocationErrorUnknown          = 0,    ///< 错误码，表示目前位置未知，但是会一直尝试获取
    TencentLBSLocationErrorDenied           = 1,    ///< 错误码，表示定位权限被禁止
    TencentLBSLocationErrorNetwork          = 2,    ///< 错误码，表示网络错误
    TencentLBSLocationErrorHeadingFailure   = 3,    ///< 错误码，表示朝向无法确认
    TencentLBSLocationErrorOther            = 4,    ///< 错误码，表示未知错误
};

/** DR 引擎启动返回码枚举 */
typedef NS_ENUM(NSInteger, TencentLBSDRStartCode) {
    TencentLBSDRStartCodeSuccess            = 0,    ///< 启动成功
    TencentLBSDRStartCodeNotSupport         = -1,   ///< 传感器有缺失或没有 GPS 芯片
    TencentLBSDRStartCodeHasStarted         = -2,   ///< 已经启动
    TencentLBSDRStartCodeSensorFailed       = -3,   ///< 传感器启动失败
    TencentLBSDRStartCodeGpsFailed          = -4,   ///< GPS 启动失败
    TencentLBSDRStartCodePermissionFailed   = -5,   ///< 没有位置权限
    TencentLBSDRStartCodeUnkown             = -6,   ///< 未知
};

/** DR 引擎运动类型枚举 */
typedef NS_ENUM(NSInteger, TencentLBSDRStartMotionType) {
    TencentLBSDRStartMotionTypeWalk                 = 2,    ///< 步行
    TencentLBSDRStartMotionTypeBike                 = 3,    ///< 骑行
};

/** 定位精度授权状态枚举 */
typedef NS_ENUM(NSInteger, TencentLBSAccuracyAuthorization) {
    TencentLBSAccuracyAuthorizationFullAccuracy,            ///< 用户授予精确定位权限
    TencentLBSAccuracyAuthorizationReducedAccuracy,         ///< 用户授予模糊定位权限，位置精度约 5km，更新频率降低，位置信息可能延迟 20 分钟
};

/**
 单次定位完成回调 Block
 @param location 定位结果，定位成功时返回位置信息
 @param error 错误信息，参考 TencentLBSLocationError
 */
typedef void (^TencentLBSLocatingCompletionBlock)(TencentLBSLocation * _Nullable location, NSError  * _Nullable error);

@protocol TencentLBSLocationManagerDelegate;

/**
 腾讯定位服务管理类，提供定位、逆地理编码、朝向、DR 惯导等核心功能。
 使用前需设置 apiKey 并确保用户同意隐私协议。

 @see TencentLBSLocationManagerDelegate
 */
#pragma mark - 🔴 TencentLBSLocationManager
@interface TencentLBSLocationManager : NSObject

/** @brief API Key，在使用定位 SDK 服务之前需要先绑定 key */
@property (nonatomic, copy) NSString* apiKey;

/** @brief 当前位置管理器定位精度的授权状态 */
@property (nonatomic, readonly) TencentLBSAccuracyAuthorization accuracyAuthorization;

/** @brief 当前位置管理器定位权限的授权状态 */
@property (nonatomic, readonly) CLAuthorizationStatus authorizationStatus;

/** @brief 实现了 TencentLBSLocationManagerDelegate 协议的代理对象 */
@property (nonatomic, weak) id<TencentLBSLocationManagerDelegate> delegate;

/** @brief 定位的最小更新距离，默认为 kCLDistanceFilterNone */
@property (nonatomic, assign) CLLocationDistance distanceFilter;

/** @brief 定位精度，默认为 kCLLocationAccuracyBest */
@property (nonatomic, assign) CLLocationAccuracy desiredAccuracy;

/** @brief 是否允许系统自动暂停定位，默认为 YES */
@property (nonatomic, assign) BOOL pausesLocationUpdatesAutomatically;

/**
 是否允许后台定位，默认为 NO
 @warning iOS 9.0 以上需要设置该选项并在 info.plist 的 Background Modes 中勾选 Location updates 才可使用后台定位权限。设置为 YES 时必须保证 Background Modes 中的 Location updates 处于选中状态，否则会抛出异常
 */
@property (nonatomic, assign) BOOL allowsBackgroundLocationUpdates;

/** @brief 用户的活动类型，默认值为 CLActivityTypeOther */
@property (nonatomic, assign) CLActivityType activityType;

/** @brief 朝向改变的最小角度阈值，只有当设备方向的改变值超过该属性值时才触发代理回调 */
@property (nonatomic, assign) CLLocationDegrees headingFilter;

/** @brief 设备当前的朝向 */
@property (nonatomic, assign) CLDeviceOrientation headingOrientation;

/** @brief 连续定位的逆地理信息请求级别，默认为 TencentLBSRequestLevelGeo */
@property (nonatomic, assign) TencentLBSRequestLevel requestLevel;

/**
 返回的 TencentLBSLocation 的 location 字段的坐标类型，默认为 TencentLBSLocationCoordinateTypeGCJ02
 @warning 在一次定位过程中只允许设置一次，不允许重复设置
 */
@property (nonatomic, assign) TencentLBSLocationCoordinateType coordinateType;

/** @brief POI 的更新间隔，默认是 10 秒 */
@property (nonatomic, assign) NSInteger poiUpdateInterval;

/** @brief 是否允许开启反作弊检查 */
@property (nonatomic, assign) BOOL enableAntiMockLocation;

/** @brief 连续定位时的回调间隔，默认为 0，单位为毫秒。连续定位且间隔大于 0 时才有效 */
@property (nonatomic, assign) uint64_t locationCallbackInterval;


#pragma mark - Utils

/** @brief 获取定位 SDK 的版本号 */
+ (NSString *)getLBSSDKVersion;

/** @brief 获取定位 SDK 的构建日期 */
+ (NSString *)getLBSSDKbuild;


#pragma mark - SDK Configuration

/**
 设置设备 ID 给定位 SDK
 @param deviceid 设备唯一标识
 */
- (void)setDeviceId:(NSString *)deviceid;

/**
 向 SDK 内部设置数据，以满足定制的需求
 @param value 设置的值
 @param key 设置的键
 */
- (void)setDataWithValue:(NSString *)value forKey:(NSString *)key;

/**
 设置用户是否同意隐私协议政策
 @param isAgree 是否同意隐私政策
 @warning 调用其他接口前必须首先调用此接口，传入 YES 后才能正常使用定位功能，否则 TencentLBSLocationManager 初始化不成功，返回 nil
 */
+ (void)setUserAgreePrivacy:(BOOL)isAgree;

/**
 获取用户是否同意隐私政策协议
 @return 是否同意隐私政策
 */
+ (BOOL)getUserAgreePrivacy;


#pragma mark - 定位权限

/** @brief 请求使用期间定位权限 */
- (void)requestWhenInUseAuthorization;

/** @brief 请求始终允许定位权限 */
- (void)requestAlwaysAuthorization;

/**
 当前属于模糊定位状态时，请求暂时的完全定位精度权限
 @param purposeKey 需要在 info.plist 中配置 NSLocationTemporaryUsageDescriptionDictionary 对应的 key 值和申请权限的描述理由
 */
- (void)requestTemporaryFullAccuracyAuthorizationWithPurposeKey:(NSString *)purposeKey;

/**
 当前属于模糊定位状态时，请求暂时的完全定位精度权限
 @param purposeKey 需要在 info.plist 中配置 NSLocationTemporaryUsageDescriptionDictionary 对应的 key 值和申请权限的描述理由
 @param completion 用户选择后的回调，如果用户授予权限则参数为 nil，否则为 PurposeKey 对应的描述信息
 */
- (void)requestTemporaryFullAccuracyAuthorizationWithPurposeKey:(NSString *)purposeKey
                                                     completion:(void (^)(NSError *))completion;

/**
 获取当前的定位精度授权状态
 @return 当前的 TencentLBSAccuracyAuthorization 状态
 */
+ (TencentLBSAccuracyAuthorization)accuracyAuthorization;


#pragma mark - 定位
#pragma mark 单次定位

/**
 单次定位，使用默认配置
 @param completionBlock 单次定位完成后的回调 Block
 @return 是否成功发起定位请求
 @warning 默认 level 为 TencentLBSRequestLevelPoi，timeout 为 10 秒。不能连续调用该接口，需在上一次返回之后才能再次发起调用
 */
- (BOOL)requestLocationWithCompletionBlock:(TencentLBSLocatingCompletionBlock)completionBlock;

/**
 单次定位，可自定义请求级别和超时时间
 @param level 定位请求级别，可根据此参数获取对应的 POI 信息
 @param timeout 获取 POI 的超时时间
 @param completionBlock 单次定位完成后的回调 Block
 @return 是否成功发起定位请求
 @warning 不能连续调用该接口，需在上一次返回之后才能再次发起调用。可通过 cancelRequestLocation 取消请求
 */
- (BOOL)requestLocationWithRequestLevel:(TencentLBSRequestLevel)level
                        locationTimeout:(NSTimeInterval)timeout
                        completionBlock:(TencentLBSLocatingCompletionBlock)completionBlock;

/** @brief 取消单次定位 */
- (void)cancelRequestLocation;

#pragma mark 连续定位

/** @brief 开始连续定位 */
- (void)startUpdatingLocation;

/** @brief 停止连续定位 */
- (void)stopUpdatingLocation;

#pragma mark 朝向

/** @brief 开始更新定位朝向 */
- (void)startUpdatingHeading;

/** @brief 停止更新定位朝向 */
- (void)stopUpdatingHeading;

/** @brief 停止展示定位朝向校准提示 */
- (void)dismissHeadingCalibrationDisplay;


#pragma mark 步骑行惯导

/**
 是否支持 DR 引擎
 @return 支持返回 YES，不支持返回 NO
 */
- (BOOL)isSupport;

/**
 启动 DR 引擎，引擎会自动获取传感器和 GPS 数据并进行位置计算
 @param type 运动类型，参考 TencentLBSDRStartMotionType
 @return 启动返回码，参考 TencentLBSDRStartCode
 @warning 请确保调用之前已获取位置权限（使用期间或者始终允许）。启动后 DR 引擎会主动开启 CLLocationManager 的 startUpdatingLocation
 */
- (TencentLBSDRStartCode)startDrEngine:(TencentLBSDRStartMotionType)type;

/**
 停止 DR 引擎
 @warning 内部有极短时间延迟，若在此期间调用 startDrEngine: 可能导致启动不成功
 */
- (void)terminateDrEngine;

/**
 主动获取 DR 实时融合位置
 @return DR 融合后的定位结果，调用 startDrEngine: 成功后才可能有值
 */
- (TencentLBSLocation *)getPosition;


#pragma mark - 测试
// 测试使用
#if TENCENTLBS_DEBUG
+ (void)upLoadData;
+ (NSData *)getLocationLog;
+ (void)newLocationLog;
#endif

@end


/**
 定位管理器代理协议，定义了定位相关的回调方法。
 包括定位权限变化、连续定位结果、朝向更新、定位失败等回调。
 */
#pragma mark - 🔴 TencentLBSLocationManagerDelegate
@protocol TencentLBSLocationManagerDelegate <NSObject>

@optional

#pragma mark 定位权限

/**
 定位权限状态改变时回调
 @param manager 定位管理器实例
 @param status 定位权限状态
 @warning 在 iOS 14 及以上废弃，由 tencentLBSDidChangeAuthorization: 代替
 */
- (void)tencentLBSLocationManager:(TencentLBSLocationManager *)manager
     didChangeAuthorizationStatus:(CLAuthorizationStatus)status;

/**
 定位权限状态改变时回调（iOS 14+ 推荐使用）
 @param manager 定位管理器实例，可通过其访问 authorizationStatus 和 accuracyAuthorization 属性
 */
- (void)tencentLBSDidChangeAuthorization:(TencentLBSLocationManager *)manager;

#pragma mark 连续定位

/**
 连续定位结果回调
 @param manager 定位管理器实例
 @param location 定位结果
 */
- (void)tencentLBSLocationManager:(TencentLBSLocationManager *)manager
                didUpdateLocation:(TencentLBSLocation *)location;

#pragma mark 朝向

/**
 定位朝向改变时回调
 @param manager 定位管理器实例
 @param newHeading 新的定位朝向
 */
- (void)tencentLBSLocationManager:(TencentLBSLocationManager *)manager
                 didUpdateHeading:(CLHeading *)newHeading;

/**
 是否展示定位朝向校准提示的回调
 @param manager 定位管理器实例
 @return 是否展示校准提示
 */
- (BOOL)tencentLBSLocationManagerShouldDisplayHeadingCalibration:(TencentLBSLocationManager *)manager;

#pragma mark 定位失败

/**
 定位发生错误时回调
 @param manager 定位管理器实例
 @param error 错误信息，参考 TencentLBSLocationError
 */
- (void)tencentLBSLocationManager:(TencentLBSLocationManager *)manager
                 didFailWithError:(NSError *)error;

#pragma mark private

/** @brief 内部调试使用，外部不应实现该接口 */
- (void)tencentLBSLocationManager:(TencentLBSLocationManager *)manager
                 didThrowLocation:(TencentLBSLocation *)location;

@end

NS_ASSUME_NONNULL_END
