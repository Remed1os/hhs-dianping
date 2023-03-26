package com.hhsdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hhsdp.dto.Result;
import com.hhsdp.entity.ShopType;
import com.hhsdp.mapper.ShopTypeMapper;
import com.hhsdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hhsdp.utils.SystemConstants.SHOP_TYPE_KEY;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result QueryTypeList() {
        //1.从redis中查询首页类型缓存
        String shopTypeListCache = stringRedisTemplate.opsForValue().get(SHOP_TYPE_KEY);

        //2.判断是否命中
        if(StrUtil.isNotBlank(shopTypeListCache)){
            //命中直接返回查询数据
            List<ShopType> typeList = JSONUtil.toList(shopTypeListCache,ShopType.class);
            return Result.ok(typeList);
        }

        //3.未命中，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //4.查询是否存在
        if (typeList == null){
            return Result.fail("未查到首页类型分类数据");
        }

        //5.将查询数据写会到cache中
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_KEY,JSONUtil.toJsonStr(typeList));

        return Result.ok();
    }
}
