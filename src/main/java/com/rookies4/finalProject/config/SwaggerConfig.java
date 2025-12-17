package com.rookies4.finalProject.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization"))) // Swagger UI에서 자동 주입
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .addServersItem(new Server().url("/"))
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("My Egg Basket API")
                .description("My Egg Basket 백엔드 API 문서입니다. Swagger UI에서 JWT Bearer 인증을 설정한 후 각 엔드포인트를 테스트할 수 있습니다.\n\n사용 방법:\n1) Swagger UI 접속: /swagger-ui/index.html\n2) 우상단 'Authorize' 클릭 후 토큰 입력 (Bearer 자동 적용)\n3) 필요한 요청 파라미터를 채우고 'Try it out'으로 테스트")
                .version("1.0");
    }
}