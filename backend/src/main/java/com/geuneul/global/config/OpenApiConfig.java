package com.geuneul.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI geuneulOpenApi() {
        return new OpenAPI().info(new Info()
                .title("그늘 (Geuneul) API")
                .description("""
                        여름 생존 지도 — 폭염·장마·벌레·화장실·식수·냉방·콘센트, 오늘 밖에서 살아남기 위한 지도.
                        간판: PostGIS 반경(ST_DWithin)/최근접(kNN) 지리검색 + 실시간 UGC 시공간 스코어링.
                        """)
                .version("v0.1 (P1 geo-core)"));
    }
}
