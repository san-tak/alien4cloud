package alien4cloud.audit.rest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import alien4cloud.audit.AuditService;
import alien4cloud.audit.model.AuditConfiguration;
import alien4cloud.audit.model.AuditTrace;
import alien4cloud.audit.model.Method;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.security.AuthorizationUtil;
import alien4cloud.security.model.User;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * This filter is used to intercept all rest call that need to be audited
 */
@Component
@Slf4j
public class AuditLogFilter extends OncePerRequestFilter implements Ordered {

    private static final ThreadLocal<Pattern> VERSION_DETECTION_PATTERN = new ThreadLocal<Pattern>() {
        @Override
        protected Pattern initialValue() {
            return Pattern.compile("/rest/(latest|[v|V]\\d+)/.+");
        }
    };

    private static final String A4C_UI_HEADER = "A4C-Agent";

    @Resource
    private AuditService auditService;

    @Resource
    private List<HandlerMapping> handlerMappings;

    private HandlerMethod getHandlerMethod(HttpServletRequest request) {
        HandlerExecutionChain handlerChain;
        try {

            handlerChain = getHandler(request);
        } catch (Exception e) {
            logger.warn("Unable to get handler method", e);
            return null;
        }
        if (handlerChain == null) {
            return null;
        }
        if (!(handlerChain.getHandler() instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handlerChain.getHandler();
        return handlerMethod;
    }

    private HandlerExecutionChain getHandler(HttpServletRequest request) {
        for (HandlerMapping handlerMapping : handlerMappings) {
            try {
                HandlerExecutionChain handlerChain = handlerMapping.getHandler(request);
                if (handlerChain != null) {
                    return handlerChain;
                }
            } catch (Exception e) {
                log.debug("Unable to get handler method", e);
            }
        }
        return null;
    }

    private ApiOperation getApiDoc(HandlerMethod method) {
        return method.getMethodAnnotation(ApiOperation.class);
    }

    private boolean isRequestContainingJson(HttpServletRequest request) {
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    private AuditTrace getAuditTrace(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod, User user, boolean requestContainsJson,
            final AuditConfiguration configuration) throws IOException {
        final Method method = auditService.getAuditedMethod(handlerMethod);
        // trace user info only when he is logged
        AuditTrace auditTrace = new AuditTrace();
        auditTrace.setTimestamp(System.currentTimeMillis());
        auditTrace.setAction(method.getAction());
        ApiOperation apiDoc = getApiDoc(handlerMethod);
        if (apiDoc != null) {
            auditTrace.setActionDescription(apiDoc.value());
        }
        auditTrace.setCategory(method.getCategory());
        auditTrace.setUserName(user.getUsername());
        auditTrace.setUserFirstName(user.getFirstName());
        auditTrace.setUserLastName(user.getLastName());
        auditTrace.setUserEmail(user.getEmail());
        // request details
        auditTrace.setPath(request.getRequestURI());
        auditTrace.setVersion(getApiVersion(request.getRequestURI()));
        auditTrace.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        auditTrace.setAlien4CloudUI(!Strings.isNullOrEmpty(request.getHeader(A4C_UI_HEADER)));
        auditTrace.setMethod(request.getMethod());
        auditTrace.setRequestParameters(request.getParameterMap());
        auditTrace.setSourceIp(request.getRemoteAddr());
        // request body
        if (requestContainsJson) {
            String original = StreamUtils.copyToString(request.getInputStream(), Charsets.UTF_8);
            auditTrace.setRequestBody(filterRequestBody(original, method));
        }
        // response details
        auditTrace.setResponseStatus(response.getStatus());
        return auditTrace;
    }

    /**
     * To filter the confidential parameters inside the request body
     * 
     * @param original A json string of request body
     * @param method A method to be audited
     * @return A new string already formatting the filtered parameters
     * @throws IOException
     */
    private String filterRequestBody(String original, final Method method) throws IOException {
        if (StringUtils.isEmpty(original) || ArrayUtils.isEmpty(method.getBodyHiddenFields())) {
            return original;
        }
        Map<String, Object> body = JsonUtil.toMap(original);
        for (String filteredParameter : method.getBodyHiddenFields()) {
            body.computeIfPresent(filteredParameter, (k, v) -> String.join("", Collections.nCopies(10, AuditConfiguration.FORMATTER)));
        }
        return JsonUtil.toString(body);
    }

    private String getApiVersion(String uri) {
        Matcher matcher = VERSION_DETECTION_PATTERN.get().matcher(uri);
        String version = null;
        if (matcher.matches()) {
            version = matcher.group(1);
        }
        return version;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        AuditConfiguration configuration = auditService.getAuditConfiguration();
        if (configuration == null || !configuration.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        User user = AuthorizationUtil.getCurrentUser();
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }
        HandlerMethod method = getHandlerMethod(request);
        if (method == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!auditService.isMethodAudited(configuration, method)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean requestContainsJson = isRequestContainingJson(request);
        if (requestContainsJson) {
            request = new MultiReadHttpServletRequest(request);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditTrace auditTrace = null;
            try {
                auditTrace = getAuditTrace(request, response, method, user, requestContainsJson, configuration);
            } catch (Exception e) {
                logger.warn("Unable to construct audit trace", e);
            }
            if (auditTrace != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug(auditTrace.toString());
                }
                try {
                    auditService.saveAuditTrace(auditTrace);
                } catch (Exception e) {
                    logger.warn("Unable to save audit trace " + auditTrace, e);
                }
            }
        }
    }
}
