package ee.openeid.siga.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.HttpServletFilterResponseWrapper;
import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.webapp.json.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.X_AUTHORIZATION_SERVICE_UUID;

@Slf4j
public class RequestDataVolumeFilter extends OncePerRequestFilter {
    private int maxRequestSize;
    private ServiceRepository serviceRepository;
    private ConnectionRepository connectionRepository;
    private static final long LIMITLESS = -1;
    private static final String OBSERVABLE_HTTP_METHOD = "POST";
    private static final String ASIC_CONTAINERS_ENDPOINT = "/containers/";
    private static final String HASHCODE_CONTAINERS_ENDPOINT = "/hashcodecontainers/";

    public RequestDataVolumeFilter(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestUrl = request.getRequestURI();
        if (OBSERVABLE_HTTP_METHOD.equals(request.getMethod()) && !isValidationReportUrl(requestUrl)) {
            long requestSize = request.getContentLengthLong();
            if (!validateRequestSize(requestSize, response)) {
                return;
            }
            Optional<SigaService> user = serviceRepository.findByUuid(request.getHeader(X_AUTHORIZATION_SERVICE_UUID.getValue()));
            if (user.isPresent()) {
                SigaService sigaService = user.get();
                if (!(sigaService.getMaxConnectionCount() == LIMITLESS && sigaService.getMaxConnectionsSize() == LIMITLESS && sigaService.getMaxConnectionSize() == LIMITLESS)) {
                    HttpServletFilterResponseWrapper wrapperResponse = new HttpServletFilterResponseWrapper(response);

                    Optional<List<SigaConnection>> optionalSigaConnections = connectionRepository.findAllByServiceId(sigaService.getId());
                    List<SigaConnection> connections = optionalSigaConnections.orElseGet(ArrayList::new);
                    boolean isRequestValid = validate(wrapperResponse, sigaService, connections, requestSize, requestUrl);
                    if (isRequestValid)
                        filterChain.doFilter(request, wrapperResponse);

                    refreshConnectionData(sigaService, requestSize, wrapperResponse, requestUrl);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void throwError(HttpServletResponse response, String message, ErrorResponseCode errorResponseCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try (OutputStream out = response.getOutputStream()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setErrorCode(errorResponseCode.name());
            errorResponse.setErrorMessage(message);
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(out, errorResponse);
            out.flush();
        }
    }

    private String getContainerIdFromUrl(String url) {
        String urlPrefix;
        if (url.contains(ASIC_CONTAINERS_ENDPOINT)) {
            urlPrefix = ASIC_CONTAINERS_ENDPOINT;
        } else if (url.contains(HASHCODE_CONTAINERS_ENDPOINT)) {
            urlPrefix = HASHCODE_CONTAINERS_ENDPOINT;
        } else {
            return null;
        }
        String suffix = url.substring(url.indexOf(urlPrefix) + urlPrefix.length());
        return suffix.substring(0, suffix.indexOf('/'));
    }

    private boolean validateRequestSize(long requestSize, HttpServletResponse response) throws IOException {
        if (requestSize > maxRequestSize) {
            log.warn("Request max size exceeded. Request size:{}", requestSize);
            throwError(response, "Request max size exceeded", ErrorResponseCode.REQUEST_SIZE_LIMIT_EXCEPTION);
            return false;
        }
        return true;
    }

    private boolean validate(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, List<SigaConnection> connections, long requestLength, String requestUrl) throws IOException {
        if (!validateConnectionsCount(wrapperResponse, sigaService, connections.size()))
            return false;
        if (!validateCurrentConnectionSize(wrapperResponse, sigaService, requestUrl, connections, requestLength))
            return false;
        long existingSize = calculateSize(connections);
        return validationConnectionsSize(wrapperResponse, sigaService, existingSize, requestLength);
    }

    private String getContainerIdFromResponse(HttpServletFilterResponseWrapper wrapperResponse) {
        String content = wrapperResponse.getContent();
        if (content == null || !content.contains("containerId"))
            return null;
        JSONObject jsonObject = new JSONObject(content);
        return jsonObject.getString("containerId");
    }

    private long calculateSize(List<SigaConnection> connections) {
        long size = 0;
        for (SigaConnection connection : connections) {
            size += connection.getSize();
        }
        return size;
    }

    private boolean isValidationReportUrl(String url) {
        return url.endsWith("containers/validationreport") || url.endsWith("hashcodecontainers/validationreport");
    }

    private boolean isNewContainerUrl(String url) {
        return url.endsWith("/containers") || url.endsWith("/hashcodecontainers");
    }

    private void refreshConnectionData(SigaService sigaService, long requestSize, HttpServletFilterResponseWrapper response, String requestUrl) {
        String containerId;
        boolean isNewContainer = isNewContainerUrl(requestUrl);
        if (isNewContainer) {
            containerId = getContainerIdFromResponse(response);
            if (containerId != null)
                insertConnectionData(requestSize, containerId, sigaService);
        } else {
            containerId = getContainerIdFromUrl(requestUrl);
            Optional<SigaConnection> connectionOptional = connectionRepository.findAllByContainerId(containerId);
            if (connectionOptional.isPresent()) {
                SigaConnection connection = connectionOptional.get();
                connection.setSize(connection.getSize() + requestSize);
                connectionRepository.saveAndFlush(connection);
            }
        }
    }

    private void insertConnectionData(long requestSize, String containerId, SigaService sigaService) {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setService(sigaService);
        sigaConnection.setContainerId(containerId);
        sigaConnection.setSize(requestSize);
        connectionRepository.saveAndFlush(sigaConnection);
    }

    private boolean validationConnectionsSize(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, double currentSize, double newSize) throws IOException {
        if (sigaService.getMaxConnectionsSize() == LIMITLESS)
            return true;
        double currentSizeInMb = (currentSize + newSize) / 1024 / 1024;
        if (currentSizeInMb >= sigaService.getMaxConnectionsSize()) {
            log.warn("Size of total connections exceeded. {} current connections size is {} MB", sigaService.getName(), currentSizeInMb);
            throwError(wrapperResponse, "Size of total connections exceeded", ErrorResponseCode.CONNECTION_LIMIT_EXCEPTION);
            return false;
        }
        return true;
    }

    private boolean validateConnectionsCount(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, int currentCount) throws IOException {
        if (sigaService.getMaxConnectionCount() == LIMITLESS)
            return true;
        if (currentCount + 1 > sigaService.getMaxConnectionCount()) {
            log.warn("Number of max connections exceeded. {} current connections count is {} ", sigaService.getName(), currentCount);
            throwError(wrapperResponse, "Number of max connections exceeded", ErrorResponseCode.CONNECTION_LIMIT_EXCEPTION);
            return false;
        }
        return true;
    }

    private boolean validateCurrentConnectionSize(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, String requestUrl, List<SigaConnection> connections, double newSize) throws IOException {
        if (sigaService.getMaxConnectionSize() == LIMITLESS) {
            return true;
        }
        long currentSize = 0;
        boolean isNewContainer = isNewContainerUrl(requestUrl);
        if (!isNewContainer) {
            String containerId = getContainerIdFromUrl(requestUrl);
            currentSize = connections.stream()
                    .filter(connection -> connection.getContainerId() != null && connection.getContainerId().equals(containerId))
                    .mapToLong(SigaConnection::getSize)
                    .sum();
        }
        double currentSizeInMb = (currentSize + newSize) / 1024 / 1024;
        if (currentSizeInMb >= sigaService.getMaxConnectionSize()) {
            log.warn("Size of connection exceeded. {} current connection size is {} MB", sigaService.getName(), currentSizeInMb);
            throwError(wrapperResponse, "Size of connection exceeded", ErrorResponseCode.CONNECTION_LIMIT_EXCEPTION);
            return false;
        }
        return true;
    }

    @Autowired
    public void setServiceRepository(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Autowired
    public void setConnectionRepository(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }


    public void setMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }
}
