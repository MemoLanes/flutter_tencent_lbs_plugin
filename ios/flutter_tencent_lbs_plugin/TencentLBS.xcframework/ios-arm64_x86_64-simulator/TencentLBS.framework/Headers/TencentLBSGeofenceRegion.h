//
//  TencentLBSGeofenceRegion.h
//  TencentLBS
//
//  Created by TencentLBS on 2026/3/30.
//  Copyright © 2026年 Tencent. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

NS_ASSUME_NONNULL_BEGIN

/** 地理围栏状态枚举，标识围栏当前的生效状态 */
typedef NS_ENUM(NSInteger, TencentLBSGeofenceRegionStatus) {
    TencentLBSGeofenceRegionStatusUnknown   = 0,    ///< 未知状态
    TencentLBSGeofenceRegionStatusPending   = 1,    ///< 等待生效
    TencentLBSGeofenceRegionStatusActive    = 2,    ///< 已生效
    TencentLBSGeofenceRegionStatusPaused    = 3,    ///< 已暂停
    TencentLBSGeofenceRegionStatusInvalid   = 4,    ///< 无效
};

/** 地理围栏触发行为枚举（位掩码），用于指定需要监听的围栏事件类型 */
typedef NS_OPTIONS(NSUInteger, TencentLBSGeofenceActiveAction) {
    TencentLBSGeofenceActiveActionNone      = 0,        ///< 不触发任何行为
    TencentLBSGeofenceActiveActionEnter     = 1 << 0,   ///< 进入围栏时触发
    TencentLBSGeofenceActiveActionExit      = 1 << 1,   ///< 离开围栏时触发
    TencentLBSGeofenceActiveActionStayed    = 1 << 2,   ///< 在围栏内停留一段时间后触发
};

/** 地理围栏类型枚举 */
typedef NS_ENUM(NSInteger, TencentLBSGeofenceRegionType) {
    TencentLBSGeofenceRegionTypeCircle      = 0,    ///< 圆形围栏
    TencentLBSGeofenceRegionTypePolygon     = 1,    ///< 多边形围栏
    TencentLBSGeofenceRegionTypeDistrict    = 2,    ///< 行政区划围栏
};


#pragma mark - TencentLBSGeofenceCoordinate

/**
 地理围栏坐标点，用于多边形围栏的顶点描述。
 使用 C 结构体以提高性能。
 */
typedef struct {
    CLLocationDegrees latitude;     ///< 纬度
    CLLocationDegrees longitude;    ///< 经度
} TencentLBSGeofenceCoordinate;

/**
 创建一个 TencentLBSGeofenceCoordinate 结构体
 @param latitude 纬度
 @param longitude 经度
 @return 围栏坐标点
 */
NS_INLINE TencentLBSGeofenceCoordinate TencentLBSGeofenceCoordinateMake(CLLocationDegrees latitude,
                                                                         CLLocationDegrees longitude) {
    TencentLBSGeofenceCoordinate coordinate;
    coordinate.latitude = latitude;
    coordinate.longitude = longitude;
    return coordinate;
}


#pragma mark - TencentLBSGeofenceRegion

/**
 地理围栏区域基类，封装围栏的公共属性。
 不应直接实例化，请使用子类 TencentLBSGeofenceCircleRegion 或 TencentLBSGeofencePolygonRegion。
 */
@interface TencentLBSGeofenceRegion : NSObject <NSSecureCoding, NSCopying>

/** @brief 围栏的唯一标识符，由调用方在创建时指定 */
@property (nonatomic, copy, readonly) NSString *identifier;

/** @brief 围栏的自定义附加信息，可用于传递业务数据 */
@property (nonatomic, copy, nullable) NSDictionary<NSString *, id<NSSecureCoding>> *userInfo;

/** @brief 围栏的中心坐标 */
@property (nonatomic, assign, readonly) CLLocationCoordinate2D center;

/** @brief 围栏当前的状态 */
@property (nonatomic, assign, readonly) TencentLBSGeofenceRegionStatus status;

/** @brief 围栏类型 */
@property (nonatomic, assign, readonly) TencentLBSGeofenceRegionType regionType;

/** @brief 围栏当前触发的行为 */
@property (nonatomic, assign, readonly) TencentLBSGeofenceActiveAction currentActiveAction;

/**
 该围栏需要触发的行为，默认为 TencentLBSGeofenceActiveActionEnter。
 可通过位运算组合多种行为，例如同时监听进入和离开：
 @code
 region.activeAction = TencentLBSGeofenceActiveActionEnter | TencentLBSGeofenceActiveActionExit;
 @endcode
 @note 需要在添加围栏之前设置，否则围栏行为可能不符合预期
 */
@property (nonatomic, assign) TencentLBSGeofenceActiveAction activeAction;

/**
 停留事件的触发时间阈值，默认为 600 秒（10 分钟）。
 当 activeAction 包含 TencentLBSGeofenceActiveActionStayed 时，用户在该围栏内
 持续停留超过此时间后会触发停留回调。
 @note 最小值为 60 秒，设置小于 60 秒时将自动修正为 60 秒
 */
@property (nonatomic, assign) NSTimeInterval stayedDuration;

/**
 判断指定坐标是否在围栏区域内
 @param coordinate 待判断的坐标
 @return 在围栏内返回 YES，否则返回 NO
 */
- (BOOL)containsCoordinate:(CLLocationCoordinate2D)coordinate;

/**
 计算指定坐标到围栏边界的距离。
 如果坐标在围栏内部，返回到最近边界的距离（正值）；
 如果坐标在围栏外部，返回到最近边界的距离（正值）。
 @param coordinate 待计算的坐标
 @return 坐标到围栏边界的距离，单位为米
 */
- (CLLocationDistance)distanceToBoundaryFromCoordinate:(CLLocationCoordinate2D)coordinate;

@end


#pragma mark - TencentLBSGeofenceCircleRegion

/**
 圆形地理围栏区域，由中心坐标和半径定义。
 */
@interface TencentLBSGeofenceCircleRegion : TencentLBSGeofenceRegion

/** @brief 圆形围栏的半径，单位为米 */
@property (nonatomic, assign, readonly) CLLocationDistance radius;

/**
 创建圆形围栏区域
 @param center 围栏中心坐标
 @param radius 围栏半径，单位为米，必须大于 0
 @param identifier 围栏的唯一标识符，不能为 nil 或空字符串
 @return 圆形围栏实例，参数无效时返回 nil
 */
- (nullable instancetype)initWithCenter:(CLLocationCoordinate2D)center
                                 radius:(CLLocationDistance)radius
                             identifier:(NSString *)identifier;

@end


#pragma mark - TencentLBSGeofencePolygonRegion

/**
 多边形地理围栏区域，由一组或多组顶点坐标定义。
 顶点按顺序连接形成闭合多边形，每个子多边形最少需要 3 个顶点。
 支持多多边形区域（如行政区划中的飞地），通过 polygons 属性访问所有子多边形。
 */
@interface TencentLBSGeofencePolygonRegion : TencentLBSGeofenceRegion

/**
 所有子多边形的坐标数组。
 数组中每个元素为一个子多边形的顶点数组（NSArray<NSValue *>，元素为 NSValue 包装的 CLLocationCoordinate2D）。
 对于单一多边形围栏，该数组仅包含一个元素。
 对于包含飞地的行政区划围栏，可能包含多个元素。

 @note containsCoordinate: 和 distanceToBoundaryFromCoordinate: 会同时考虑所有子多边形。
       当两个 polygon 重叠时，重叠区域不属于本区域（飞地语义）。
 */
@property (nonatomic, copy, readonly) NSArray<NSArray<NSValue *> *> *polygons;

/**
 创建单多边形围栏区域
 @param coordinates 多边形顶点坐标数组，数组中的元素为 NSValue 包装的 CLLocationCoordinate2D。最少需要 3 个顶点，且各顶点坐标须为有效经纬度
 @param identifier 围栏的唯一标识符，不能为 nil 或空字符串
 @return 多边形围栏实例，参数无效时返回 nil
 */
- (nullable instancetype)initWithCoordinates:(NSArray<NSValue *> *)coordinates
                                  identifier:(NSString *)identifier;

/**
 创建多多边形围栏区域（支持飞地等复合区域）
 @param polygons 多边形数组，每个元素为一个子多边形的顶点坐标数组（NSArray<NSValue *>），每个子多边形最少需要 3 个顶点
 @param identifier 围栏的唯一标识符，不能为 nil 或空字符串
 @return 多边形围栏实例，参数无效时返回 nil
 */
- (nullable instancetype)initWithPolygons:(NSArray<NSArray<NSValue *> *> *)polygons
                               identifier:(NSString *)identifier;

/**
 创建单多边形围栏区域（C 数组方式）
 @param coordinates 多边形顶点坐标 C 数组
 @param count 顶点数量，最少需要 3 个顶点
 @param identifier 围栏的唯一标识符，不能为 nil 或空字符串
 @return 多边形围栏实例，参数无效时返回 nil
 */
- (nullable instancetype)initWithCCoordinates:(const CLLocationCoordinate2D *)coordinates
                                        count:(NSUInteger)count
                                   identifier:(NSString *)identifier;

@end


#pragma mark - TencentLBSGeofenceDistrictRegion

/**
 行政区划地理围栏区域，由行政区划关键字创建，通过网络查询获取边界多边形数据。
 继承自 TencentLBSGeofencePolygonRegion，具有多边形围栏的所有检测能力。
 */
@interface TencentLBSGeofenceDistrictRegion : TencentLBSGeofencePolygonRegion

/** @brief 创建围栏时使用的行政区划关键字（如"海淀区"） */
@property (nonatomic, copy, readonly) NSString *keyword;

/** @brief 行政区划代码（6 位数字，如 110108 表示海淀区） */
@property (nonatomic, copy, readonly) NSString *adcode;

/** @brief 行政区划全称（如"海淀区"） */
@property (nonatomic, copy, readonly) NSString *districtName;

/** @brief 行政区划层级描述（如"区"、"市"、"省"） */
@property (nonatomic, copy, readonly, nullable) NSString *districtLevel;

/**
 内部构造方法，由 TencentLBSGeofenceManager 在网络请求成功后调用。
 @param polygons 行政区划边界多边形数组，每个元素为一个子多边形的顶点坐标数组
 @param identifier 围栏的唯一标识符
 @param keyword 行政区划搜索关键字
 @param adcode 行政区划代码
 @param districtName 行政区划全称
 @param districtLevel 行政区划层级描述（如"区"、"市"、"省"），可为 nil
 @return 行政区划围栏实例，参数无效时返回 nil
 */
- (nullable instancetype)initWithPolygons:(NSArray<NSArray<NSValue *> *> *)polygons
                               identifier:(NSString *)identifier
                                  keyword:(NSString *)keyword
                                   adcode:(NSString *)adcode
                             districtName:(NSString *)districtName
                            districtLevel:(nullable NSString *)districtLevel;

@end

NS_ASSUME_NONNULL_END
