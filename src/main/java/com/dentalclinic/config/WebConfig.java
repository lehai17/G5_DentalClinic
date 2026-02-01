    package com.dentalclinic.config;

    import org.springframework.context.annotation.Configuration;
    import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
    import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

    @Configuration
    public class WebConfig implements WebMvcConfigurer {

        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            // "/" công khai -> trang chủ (khách chưa đăng nhập chỉ xem thông tin)
            registry.addViewController("/").setViewName("redirect:/homepage");
        }
    }
