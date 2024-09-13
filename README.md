## 项目功能

* 项目包含两部分：**乘客端、司机端**

* 乘客端：

​	登录--选择代驾地址--预估订单数据--呼叫代驾--等待接单--15分钟没有司机接单自动取消

​	15分钟内有司机接单--司乘同显--结束代驾--接收账单--领取/使用优惠券--发起微信支付

* 司机端

​	登录--实名认证--开始接单--司机抢单--前往代驾地点--上传代驾车辆信息--开始代驾--结束代驾--生成账单--推送账单--系统分账	

- 订单状态变化

  等待司机接单--无人接单自动取消

  等待司机接单--司机已接单--司机已到达--司机上传代驾车辆信息--开始服务--结束服务--乘客待付款--乘客已付款--订单完成

## 技术概述

- **SpringBoot**：简化Spring应用的初始搭建以及开发过程
- **SpringCloud**：基于Spring Boot实现的云原生应用开发工具，SpringCloud使用的技术：（Spring Cloud Gateway、Spring Cloud Task和Spring Cloud Feign等）
- **SpringBoot+SpringCloudAlibaba(Nacos，Sentinel) + OpenFeign + Gateway**
- MyBatis-Plus：持久层框架，也依赖mybatis
- Redis：内存做缓存
- Redisson：基于redis的Java驻内存数据网格 - 框架；操作redis的框架
- MongoDB: 分布式文件存储的数据库
- RabbitMQ：消息中间件；大型分布式项目是标配；分布式事务最终一致性
- Seata：分布式事务
- Drools：规则引擎，计算预估费用、取消费用等等
- GEO：GPS分区定位计算
- ThreadPoolExecutor+CompletableFuture：异步编排，线程池来实现异步操作，提高效率
- XXL-JOB: 分布式定时任务调用中心
- Knife4J/YAPI：Api接口文档工具
- MinIO（私有化对象存储集群）：分布式文件存储 类似于OSS（公有）
- 微信支付：微信支付与微信分账
- MySQL：关系型数据库 {shardingSphere-jdbc 进行读写分离; 分库，分表}
- Lombok: 实体类的中get/set 生成的jar包
- Natapp：内网穿透
- Docker：容器化技术;  生产环境Redis（运维人员）；快速搭建环境Docker run
