package com.deepoove.swagger.dubbo.web;

import com.deepoove.swagger.dubbo.config.DubboPropertyConfig;
import com.deepoove.swagger.dubbo.config.DubboServiceScanner;
import com.deepoove.swagger.dubbo.config.SwaggerDocCache;
import com.deepoove.swagger.dubbo.reader.Reader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import io.swagger.annotations.Api;
import io.swagger.config.SwaggerConfig;
import io.swagger.models.Swagger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponents;
import springfox.documentation.spring.web.json.Json;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static springfox.documentation.swagger.common.HostNameProvider.componentsFrom;

@Controller
@RequestMapping("${swagger.dubbo.doc:swagger-dubbo}")
@Api(hidden = true)
public class SwaggerDubboController {

    public static final String DEFAULT_URL = "/api-docs";
    private static final String HAL_MEDIA_TYPE = "application/hal+json";

    private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";

    @Autowired
    private DubboServiceScanner dubboServiceScanner;
    @Autowired
    private DubboPropertyConfig dubboPropertyConfig;
    @Autowired
    private SwaggerDocCache swaggerDocCache;

    @Value("${swagger.dubbo.http:h}")
    private String httpContext;
    @Value("${swagger.dubbo.enable:true}")
    private boolean enable = true;

    @RequestMapping(value = DEFAULT_URL,
            method = RequestMethod.GET,
            produces = {"application/json; charset=utf-8", HAL_MEDIA_TYPE})
    @ResponseBody
    public ResponseEntity<Json> getApiList(HttpServletRequest servletRequest) throws JsonProcessingException {
        String contextPath = servletRequest.getContextPath();
        servletRequest.getHeader(X_FORWARDED_PREFIX);
        if (!enable) {
            return new ResponseEntity<Json>(HttpStatus.NOT_FOUND);
        }

        Swagger swagger = swaggerDocCache.getSwagger();
        if (null != swagger) {
            return new ResponseEntity<Json>(new Json(io.swagger.util.Json.mapper().writeValueAsString(swagger)), HttpStatus.OK);
        } else {
            swagger = new Swagger();
        }

        final SwaggerConfig configurator = dubboPropertyConfig;
        if (configurator != null) {
            configurator.configure(swagger);
        }

        /**
         * 调整path
         * 参考springfox写法，这里做了精简、调整
         * 不保证所有情况下都对，具体参考springfox的写法
         * @see springfox.documentation.swagger2.web.Swagger2Controller#getDocumentation(String, HttpServletRequest)
         * @see springfox.documentation.swagger.common.XForwardPrefixPathAdjuster#adjustedPath(String)
         */
        UriComponents uriComponents = componentsFrom(servletRequest, swagger.getBasePath());
        swagger.basePath(Strings.isNullOrEmpty(uriComponents.getPath()) ? "/" : uriComponents.getPath());

        Map<Class<?>, Object> interfaceMapRef = dubboServiceScanner.interfaceMapRef();
        if (null != interfaceMapRef) {
            Reader.read(swagger, interfaceMapRef, httpContext);
        }
        swaggerDocCache.setSwagger(swagger);
        return new ResponseEntity<Json>(new Json(io.swagger.util.Json.mapper().writeValueAsString(swagger)), HttpStatus.OK);
    }

}
