# hhs-dianping
嗨好食点评
嗨好食点评是一个点评类项目，此项目分为后台管理部分和前台使用部分，实现了发布查看商家，达人探 店，点赞，关注，为用户提供查看提供附近消费场所等功能来帮助商家引流，增加曝光度。
短信登录
使用 redis 共享 session 来实现

商户查询缓存
理解缓存击穿，缓存穿透，缓存雪崩等问题

优惠卷秒杀
结合 Lua 完成高性能的 redis 操作，Redis 分布式锁的原理， Redis 的三种消息队列

附近的商户
利用 Redis 的 GEOHash 来完成对于地理坐标的操作

UV 统计
主要是使用 Redis 来完成统计功能

用户签到
使用 Redis 的 BitMap 数据统计功能

好友关注
基于 Set 集合的关注、取消关注，共同关注等等功能

打人探店
基于 List 来完成点赞列表的操作，同时基于 SortedSet 来完成点赞的排行榜功能
