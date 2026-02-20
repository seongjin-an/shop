package com.ansj.shopm.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@RequiredArgsConstructor
@EnableRedisHttpSession(
//        redisNamespace = "shop_m:user_session",
        maxInactiveIntervalInSeconds = 30 * 60,// 30분
        flushMode = FlushMode.IMMEDIATE // 속성이 변경되면 바로 저장된다.
)
@Configuration
public class RedisSessionConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        // 내 커스텀 클래스를 등록 (Mixin 사용)
        //objectMapper.addMixIn(CustomUserDetails.class, CustomUserDetailsMixin.class);

        // ⭐️ 3. 이 설정이 핵심입니다!
        // JSON의 "@class" 필드를 읽어서 실제 객체 타입으로 변환할 수 있게 해줍니다.
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

}

