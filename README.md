# Leaf

> There are no two identical leaves in the world.
>
> 世界上没有两片完全相同的树叶。
>
> ​								— 莱布尼茨

[文档](./README.md)

## Introduction

Leaf 最早期需求是各个业务线的订单ID生成需求。有的业务直接通过DB自增的方式生成ID，有的业务通过redis缓存来生成ID，也有的业务直接用UUID这种方式来生成ID。以上的方式各自有各自的问题，因此我们决定实现一套分布式ID生成服务来满足需求。具体Leaf 设计文档见：[ leaf 美团分布式ID生成服务 ](https://tech.meituan.com/MT_Leaf.html )

## Quick Start

### 使用leaf-starter注解来启动leaf

```shell script
git clone git@github.com:ci-plugins/Leaf.git
git checkout master
cd leaf
mvn clean install -Dmaven.test.skip=true 
```
#### 引入依赖
```xml
<dependency>
    <artifactId>leaf-boot-starter</artifactId>
    <groupId>com.tencent.devops.leaf</groupId>
    <version>1.0.2-RELEASE</version>
</dependency>
```
#### 配置yml文件
```yml
leaf:
  segment:
    allocStrategyDaoBeanName: jooqIDAllocDaoImpl
    enable: true
    url: jdbc:mysql://xxxxxx/devops_project?useSSL=false&autoReconnect=true&serverTimezone=GMT%2B8&useUnicode=true&characterEncoding=utf8&allowMultiQueries=true
    username: xxx
    password: xxx
  snowflake:
    enable: false
    address: 
    port:
  name: dev-leaf
```
#### 利用注解启动leaf，并使用api
```java
//EnableLeafServer 开启leafserver
@SpringBootApplication
@EnableLeafServer
public class LeafdemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeafdemoApplication.class, args);
	}
}
//直接使用 spring注入
public class T {
    @Autowired
    private SegmentService segmentService;
    @Autowired
    private SnowflakeService snowflakeService;
}
```

### 使用注解启动leaf
https://github.com/ci-plugins/Leaf/blob/master/README.md

### Leaf Server

我们提供了一个基于spring boot的HTTP服务来获取ID


#### 配置介绍

Leaf 提供两种生成的ID的方式（号段模式和snowflake模式），你可以同时开启两种方式，也可以指定开启某种方式（默认两种方式为关闭状态）。

Leaf Server 支持通过 YAML 或环境变量配置。环境变量模板见 [`leaf-server/.env.example`](./leaf-server/.env.example)。

| 配置项                    | 含义                          | 默认值 |
| ------------------------- | ----------------------------- | ------ |
| leaf.name                 | leaf 服务名                   |        |
| leaf.segment.enable       | 是否开启号段模式              | false  |
| leaf.segment.allocStrategyDaoBeanName       | 号段模式dao层实现策略（不配默认使用mybatis）              |   |
| leaf.segment.url             | mysql 库地址                  |        |
| leaf.segment.username        | mysql 用户名                  |        |
| leaf.segment.password        | mysql 密码                    |        |
| leaf.snowflake.enable     | 是否开启snowflake模式         | false  |
| leaf.snowflake.address | snowflake模式下的zk地址       |        |
| leaf.snowflake.port       | snowflake模式下的服务注册端口 |        |

环境变量使用 Spring Boot 的映射规则：配置项改为大写，并用下划线分隔。例如 `leaf.segment.enable` 对应 `LEAF_SEGMENT_ENABLE`，`leaf.snowflake.address` 对应 `LEAF_SNOWFLAKE_ADDRESS`。模板也包含本地 MySQL 的 Docker Compose 配置和初始化 SQL。首次运行时，在 `leaf-server` 目录中执行：

```shell
cp .env.example .env
docker compose --env-file .env up -d mysql
docker compose ps
set -a
. ./.env
set +a
mvn spring-boot:run
```

MySQL 首次初始化会创建 `T_LEAF_ALLOC` 并写入 `leaf-segment-test`。示例账号仅供本机开发，部署到共享或生产环境前请更换密码。`.env` 已加入忽略规则，避免本地密码误提交。

#### 号段模式

如果使用号段模式，需要建立DB表，并配置leaf.segment.url, leaf.segment.username, leaf.segment.password;
如果dao层不用mybatis实现,那么用户需要在自已的工程中定义一个继承IDAllocDao接口的dao实现类，并且把leaf.segment.allocStrategyDaoBeanName
该配置的值指定为dao实现类在spring中的bean名称。

如果不想使用该模式配置leaf.segment.enable=false即可。

##### 创建数据表

```sql
CREATE DATABASE leaf
CREATE TABLE `T_LEAF_ALLOC` (
  `BIZ_TAG` varchar(128)  NOT NULL DEFAULT '',
  `MAX_ID` bigint(20) NOT NULL DEFAULT '1',
  `STEP` int(11) NOT NULL,
  `DESCRIPTION` varchar(256)  DEFAULT NULL,
  `UPDATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`BIZ_TAG`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4;

insert into T_LEAF_ALLOC(BIZ_TAG, MAX_ID, STEP, DESCRIPTION) values('leaf-segment-test', 1, 2000, 'Test leaf Segment Mode Get Id')
```

##### 配置相关数据项

在yml中配置leaf.segment.url, leaf.segment.username, leaf.segment.password参数

#### Snowflake模式

算法取自twitter开源的snowflake算法。

如果不想使用该模式配置leaf.snowflake.enable=false即可。

##### 配置zookeeper地址

配置 `leaf.snowflake.address` 和 `leaf.snowflake.port`（对应环境变量 `LEAF_SNOWFLAKE_ADDRESS` 和 `LEAF_SNOWFLAKE_PORT`）。
#### 运行Leaf Server

##### 打包服务

```shell
git clone git@github.com:ci-plugins/Leaf.git
//按照上面的号段模式在工程里面配置好
cd leaf
mvn clean install -DskipTests
cd leaf-server
```

##### 运行服务

*注意:首先得先配置好数据库表或者zk地址*
###### mvn方式

```shell
mvn spring-boot:run
```

###### 脚本方式

```shell
sh deploy/run.sh
```
##### 测试

```shell
#segment
curl http://localhost:8080/api/segment/get/leaf-segment-test
#snowflake
curl http://localhost:8080/api/snowflake/get/test
```

##### 监控页面

号段模式：http://localhost:8080/cache

### Leaf Core

当然，为了追求更高的性能，需要通过RPC Server来部署Leaf 服务，那仅需要引入leaf-core的包，把生成ID的API封装到指定的RPC框架中即可。

### 注意事项
注意现在leaf使用snowflake模式的情况下 其获取ip的逻辑直接取首个网卡ip【特别对于会更换ip的服务要注意】避免浪费workId
