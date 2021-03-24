package conflux.dex.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DexMvcConfigurer implements WebMvcConfigurer {
	
	@Override
	@CrossOrigin
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**");
	}

}
