//
//  TencentLBSGeofenceManager.h
//  TencentLBS
//
//  Created by TencentLBS on 2026/3/30.
//  Copyright © 2026年 Tencent. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import "TencentLBSGeofenceRegion.h"
#import "TencentLBSLocationManager.h"

NS_ASSUME_NONNULL_BEGIN

/** 地理围栏错误码枚举 */
typedef NS_ENUM(NSInteger, TencentLBSGeofenceError) {
    TencentLBSGeofenceErrorUnknown              = 0,    ///< 未知错误
    TencentLBSGeofenceErrorInvalidParameter     = 1,    ///< 参数无效
    TencentLBSGeofenceErrorLocationNotAvailable = 2,    ///< 定位不可用
    TencentLBSGeofenceErrorDenied               = 3,    ///< 定位权限被拒绝
    TencentLBSGeofenceErrorDuplicate            = 4,    ///< 围栏标识符重复
    TencentLBSGeofenceErrorNetworkFailed        = 5,    ///< 网络请求失败（行政区划围栏）
    TencentLBSGeofenceErrorDistrictNotFound     = 6,    ///< 未找到匹配的行政区划
    TencentLBSGeofenceErrorDistrictDataInvalid  = 7,    ///< 行政区划数据无效（缺少边界数据等）
};

/** 地理围栏错误域 */
FOUNDATION_EXPORT NSErrorDomain const TencentLBSGeofenceErrorDomain;

@protocol TencentLBSGeofenceManagerDelegate;

/**
 腾讯地理围栏管理类，提供围栏的创建、监控和管理功能。
 支持圆形围栏和多边形围栏，可监听进入、离开和停留事件。

 使用步骤：
 1. 创建 TencentLBSGeofenceManager 实例并设置 delegate
 2. 设置 coordinateType 指定围栏坐标系（默认 GCJ-02，与腾讯地图一致）
 3. 创建围栏对象，设置 activeAction（监听事件类型，如 Enter/Exit/Stayed）和 stayedDuration（停留阈值）等围栏级别属性
 4. 调用 addGeofenceRegion: 添加围栏（坐标系需与 coordinateType 一致）
 5. 在代理回调中处理围栏事件

 @see TencentLBSGeofenceManagerDelegate
 @see TencentLBSGeofenceRegion
 */
#pragma mark - TencentLBSGeofenceManager
@interface TencentLBSGeofenceManager : NSObject

/** @brief 实现了 TencentLBSGeofenceManagerDelegate 协议的代理对象 */
@property (nonatomic, weak, nullable) id<TencentLBSGeofenceManagerDelegate> delegate;

/** @brief 是否允许后台位置更新，默认为 NO */
@property (nonatomic, assign) BOOL allowsBackgroundLocationUpdates;

/** @brief 当前已添加的所有围栏区域 */
@property (nonatomic, copy, readonly) NSArray<TencentLBSGeofenceRegion *> *geofenceRegions;

/** @brief 定位精度，默认为 kCLLocationAccuracyBest */
@property (nonatomic, assign) CLLocationAccuracy desiredAccuracy;

/** @brief 定位的最小更新距离，默认为 kCLDistanceFilterNone */
@property (nonatomic, assign) CLLocationDistance distanceFilter;

/**
 检测频率间隔，默认为 0 秒（每次位置更新都检测）。
 用于控制围栏检测的频率，降低性能消耗。
 */
@property (nonatomic, assign) NSTimeInterval detectionInterval;

/**
 是否启用自适应距离过滤器（智能频率控制），默认为 YES。

 开启时，SDK 会根据当前位置到最近围栏边界的距离，自动调整定位频率。
 距离围栏越近定位频率越高，距离越远频率越低，以平衡检测精度与功耗。

 关闭时，使用 distanceFilter 属性设置的固定值。

 @warning 开启自适应模式后，手动设置的 distanceFilter 值将不会生效，直到关闭自适应模式。
 */
@property (nonatomic, assign, getter=isAdaptiveDistanceFilterEnabled) BOOL adaptiveDistanceFilterEnabled;

/** @brief 当前实际生效的距离过滤值（只读）。自适应模式下会动态变化，固定模式下等于 distanceFilter。 */
@property (nonatomic, readonly) CLLocationDistance currentDistanceFilter;

/**
 围栏使用的坐标系类型，默认为 TencentLBSLocationCoordinateTypeGCJ02。
 用于指定传入的围栏坐标和回调的定位坐标所使用的坐标系。

 - TencentLBSLocationCoordinateTypeGCJ02: 火星坐标（国测局坐标），与腾讯地图一致。
   系统定位获取的 WGS-84 坐标会自动转换为 GCJ-02 后再与围栏坐标进行比较。
   在中国大陆地区会进行坐标转换，海外地区不进行转换。
 - TencentLBSLocationCoordinateTypeWGS84: 地球坐标，直接使用系统定位的原始坐标。

 @warning 需要在添加围栏之前设置，且围栏坐标系需与此设置保持一致，否则会导致围栏判断偏差。

 */
@property (nonatomic, assign) TencentLBSLocationCoordinateType coordinateType;


#pragma mark - 围栏管理

/**
 添加圆形地理围栏
 @param center 围栏中心坐标，坐标系需与 coordinateType 设置一致
 @param radius 围栏半径，单位为米
 @param identifier 围栏的唯一标识符
 @note 建议单个 Manager 实例的围栏数量不超过 1000 个，过多的围栏会增加位置检测的计算开销
 */
- (void)addCircleGeofenceWithCenter:(CLLocationCoordinate2D)center
                             radius:(CLLocationDistance)radius
                         identifier:(NSString *)identifier;

/**
 添加圆形地理围栏（附带自定义数据）
 @param center 围栏中心坐标
 @param radius 围栏半径，单位为米
 @param identifier 围栏的唯一标识符
 @param userInfo 自定义附加信息
 */
- (void)addCircleGeofenceWithCenter:(CLLocationCoordinate2D)center
                             radius:(CLLocationDistance)radius
                         identifier:(NSString *)identifier
                           userInfo:(nullable NSDictionary<NSString *, id<NSSecureCoding>> *)userInfo;

/**
 添加多边形地理围栏
 @param coordinates 多边形顶点坐标数组，元素为 NSValue 包装的 CLLocationCoordinate2D，最少需要 3 个顶点
 @param identifier 围栏的唯一标识符
 */
- (void)addPolygonGeofenceWithCoordinates:(NSArray<NSValue *> *)coordinates
                               identifier:(NSString *)identifier;

/**
 添加多边形地理围栏（附带自定义数据）
 @param coordinates 多边形顶点坐标数组，元素为 NSValue 包装的 CLLocationCoordinate2D，最少需要 3 个顶点
 @param identifier 围栏的唯一标识符
 @param userInfo 自定义附加信息
 */
- (void)addPolygonGeofenceWithCoordinates:(NSArray<NSValue *> *)coordinates
                               identifier:(NSString *)identifier
                                 userInfo:(nullable NSDictionary<NSString *, id<NSSecureCoding>> *)userInfo;

/**
 添加多边形地理围栏（C 数组方式）
 @param coordinates 多边形顶点坐标 C 数组
 @param count 顶点数量，最少需要 3 个
 @param identifier 围栏的唯一标识符
 */
- (void)addPolygonGeofenceWithCCoordinates:(const CLLocationCoordinate2D *)coordinates
                                     count:(NSUInteger)count
                                identifier:(NSString *)identifier;

/**
 添加已创建的围栏区域对象
 @param region 围栏区域对象，支持 TencentLBSGeofenceCircleRegion、TencentLBSGeofencePolygonRegion 和 TencentLBSGeofenceDistrictRegion
 */
- (void)addGeofenceRegion:(TencentLBSGeofenceRegion *)region;

/**
 添加行政区划围栏（关键字方式）。
 keyword 支持两种形式：
 - 纯数字（如 @"110108"）：视为行政区划代码（adcode），直接查询边界多边形数据
 - 中文名称（如 @"朝阳区"）：先搜索匹配的行政区划，再查询边界多边形数据

 如果使用中文名称且存在多个同名行政区划（如"朝阳区"同时匹配北京市朝阳区和长春市朝阳区），
 会为每个匹配结果都创建围栏，identifier 自动追加 "_序号" 后缀（如 "district_朝阳区_0"、"district_朝阳区_1"）。
 每个围栏创建完成（成功或失败）都会单独回调
 tencentLBSGeofenceManager:didCreateDistrictRegion:forKeyword:error: 。

 @param keyword 行政区划关键字，可以是 adcode（如 @"110108"）或中文名称（如 @"海淀区"）
 @param identifier 围栏的基础标识符。当匹配到多个行政区划时，实际标识符会追加 "_序号" 后缀
 @warning 此方法为异步操作，围栏不会立即添加。请在代理回调中确认围栏创建是否成功。
 */
- (void)addDistrictGeofenceWithKeyword:(NSString *)keyword
                            identifier:(NSString *)identifier;

/**
 添加行政区划围栏（关键字方式，附带自定义数据）
 @param keyword 行政区划关键字，可以是 adcode 或中文名称
 @param identifier 围栏的基础标识符
 @param userInfo 自定义附加信息
 */
- (void)addDistrictGeofenceWithKeyword:(NSString *)keyword
                            identifier:(NSString *)identifier
                              userInfo:(nullable NSDictionary<NSString *, id<NSSecureCoding>> *)userInfo;

/**
 根据标识符移除指定围栏
 @param identifier 要移除的围栏标识符
 */
- (void)removeGeofenceWithIdentifier:(NSString *)identifier;

/**
 移除指定围栏区域对象
 @param region 要移除的围栏区域对象
 */
- (void)removeGeofenceRegion:(TencentLBSGeofenceRegion *)region;

/** @brief 移除所有地理围栏 */
- (void)removeAllGeofences;

/**
 根据标识符获取围栏区域
 @param identifier 围栏标识符
 @return 对应的围栏区域对象，未找到时返回 nil
 */
- (nullable TencentLBSGeofenceRegion *)geofenceRegionWithIdentifier:(NSString *)identifier;


#pragma mark - 围栏状态查询

/**
 获取指定坐标所在的围栏列表
 @param coordinate 待查询的坐标
 @return 包含该坐标的围栏区域数组
 */
- (NSArray<TencentLBSGeofenceRegion *> *)geofenceRegionsContainingCoordinate:(CLLocationCoordinate2D)coordinate;

/**
 主动查询指定坐标相对于指定围栏的状态
 @param coordinate 待查询的坐标
 @param identifier 围栏标识符
 @return 坐标在围栏内返回 YES，否则返回 NO
 */
- (BOOL)isCoordinate:(CLLocationCoordinate2D)coordinate insideGeofenceWithIdentifier:(NSString *)identifier;


#pragma mark - 围栏监控

/** @brief 开始监控所有已添加的围栏 */
- (void)startMonitoring;

/** @brief 停止监控所有围栏 */
- (void)stopMonitoring;

/** @brief 暂停围栏监控，保留围栏数据但暂停位置检测 */
- (void)pauseMonitoring;

/** @brief 恢复已暂停的围栏监控 */
- (void)resumeMonitoring;

/** @brief 围栏监控是否正在运行 */
@property (nonatomic, assign, readonly, getter=isMonitoring) BOOL monitoring;

@end


#pragma mark - TencentLBSGeofenceManagerDelegate

/**
 地理围栏管理器代理协议，定义了围栏相关的回调方法。
 包括围栏事件触发、围栏状态变化和错误回调。
 */
@protocol TencentLBSGeofenceManagerDelegate <NSObject>

@optional

/**
 进入围栏时回调
 
    @param manager 围栏管理器实例
 @param region 触发事件的围栏区域
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
               didEnterRegion:(TencentLBSGeofenceRegion *)region;

/**
 离开围栏时回调
 @param manager 围栏管理器实例
 @param region 触发事件的围栏区域
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
                didExitRegion:(TencentLBSGeofenceRegion *)region;

/**
 在围栏内停留达到阈值时回调
 @param manager 围栏管理器实例
 @param region 触发事件的围栏区域
 @param duration 已停留的时间，单位为秒
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
             didStayInRegion:(TencentLBSGeofenceRegion *)region
                 forDuration:(NSTimeInterval)duration;

/**
 围栏状态改变时回调
 @param manager 围栏管理器实例
 @param region 状态发生变化的围栏区域
 @param status 围栏新的状态
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
         didChangeStatusForRegion:(TencentLBSGeofenceRegion *)region
                           status:(TencentLBSGeofenceRegionStatus)status;

/**
 围栏添加成功时回调，返回围栏对应的 CLRegion（仅圆形围栏可用）
 @param manager 围栏管理器实例
 @param region 添加成功的围栏区域
 @param clRegion 对应的 CLRegion 对象，多边形围栏为 nil
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
               didAddRegion:(TencentLBSGeofenceRegion *)region
               withCLRegion:(nullable CLRegion *)clRegion;

/**
 围栏相关的定位位置更新回调
 @param manager 围栏管理器实例
 @param location 与 coordina最新的位置信息，坐标系teType 设置一致\

 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
             didUpdateLocation:(CLLocation *)location;

/**
 围栏操作发生错误时回调
 @param manager 围栏管理器实例
 @param error 错误信息，参考 TencentLBSGeofenceError
 @param region 出错关联的围栏区域，可能为 nil
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
              didFailWithError:(NSError *)error
                     forRegion:(nullable TencentLBSGeofenceRegion *)region;

/**
 行政区划围栏创建完成时回调（异步）。
 无论成功或失败都会回调此方法。成功时 region 不为 nil，error 为 nil；
 失败时 region 为 nil，error 包含错误信息。

 keyword 可能是中文名称或 adcode。如果是中文名称且匹配到多个行政区划，
 每个结果都会单独回调此方法。

 @param manager 围栏管理器实例
 @param region 创建成功的行政区划围栏区域，失败时为 nil
 @param keyword 创建时使用的关键字（可能是 adcode 或中文名称）
 @param error 错误信息，成功时为 nil
 */
- (void)tencentLBSGeofenceManager:(TencentLBSGeofenceManager *)manager
       didCreateDistrictRegion:(nullable TencentLBSGeofenceDistrictRegion *)region
                    forKeyword:(NSString *)keyword
                         error:(nullable NSError *)error;

@end

NS_ASSUME_NONNULL_END
