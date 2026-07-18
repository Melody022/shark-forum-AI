package com.itswy.paicodingai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itswy.paicodingai.entity.ChatSession;
import com.itswy.paicodingai.mapper.ChatSessionMapper;
import com.itswy.paicodingai.config.SessionProperties;
import com.itswy.paicodingai.vo.SessionVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.util.*;

public interface ChatSessionService extends IService<ChatSession> {

    SessionVO createSession(Integer num);

}
