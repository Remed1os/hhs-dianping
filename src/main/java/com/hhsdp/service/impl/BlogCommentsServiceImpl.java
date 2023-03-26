package com.hhsdp.service.impl;

import com.hhsdp.entity.BlogComments;
import com.hhsdp.mapper.BlogCommentsMapper;
import com.hhsdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
