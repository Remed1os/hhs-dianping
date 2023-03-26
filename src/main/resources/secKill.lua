-- 1.参数列表

--优惠券id
local voucherId = ARVG[1]
--用户id
local userId = ARVG[2]
--订单id
local orderId = ARVG[3]

--库存key          -- .. 表示 +
local storeKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId


--3.1判断库存是否充足
if(tonumber(redis.call('get',storeKey)) <= 0) then
    --库存不足
    return 1
end

--3.2判断用户是否下单。  使用set集合，无序唯一，只可以存在一个
if(tonumber(redis.call('sisnumber',orderKey)) == 1) then
    --存在，说明重复下单，返回2
    return 2
end

--3.3扣库存
redis.call('incrby',storeKey,-1)
--3.4下单。使用set集合，无序唯一，只可以存在一个
redis.call('sadd',orderKey,userId)
--3.5添加订单信息到队列中
redis.call('xadd','stream:orders','voucherId',voucherId,'userId',userId,'id',orderId)

--下单成功返回0
return 0



