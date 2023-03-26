package com.hhsdp.service;

import com.hhsdp.dto.Result;
import com.hhsdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopTypeService extends IService<ShopType> {

    Result QueryTypeList();
}
