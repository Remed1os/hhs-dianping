package com.hhsdp.service;

import com.hhsdp.dto.Result;
import com.hhsdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
