package ee.openeid.siga.service.signature.container.detached;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.HashcodeSignatureWrapper;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.service.signature.session.DetachedDataFileSessionHolder;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class DetachedDataFileContainerService implements DetachedDataFileSessionHolder {

    private SessionService sessionService;
    private Configuration configuration;

    public String createContainer(List<HashcodeDataFile> dataFiles) {

        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        dataFiles.forEach(hashcodeContainer::addDataFile);
        OutputStream outputStream = new ByteArrayOutputStream();
        hashcodeContainer.save(outputStream);

        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, transformContainerToSession(sessionId, hashcodeContainer));
        return sessionId;
    }

    public String uploadContainer(String container) {
        String sessionId = SessionIdGenerator.generateSessionId();
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(container.getBytes()));
        hashcodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(sessionId, hashcodeContainer));
        return sessionId;
    }

    public String getContainer(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);

        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        sessionHolder.getSignatures().forEach(signatureWrapper -> hashcodeContainer.getSignatures().add(signatureWrapper));
        sessionHolder.getDataFiles().forEach(dataFile -> hashcodeContainer.getDataFiles().add(dataFile));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        hashcodeContainer.save(outputStream);
        byte[] container = outputStream.toByteArray();

        return new String(Base64.getEncoder().encode(container));
    }


    public String closeSession(String containerId) {
        sessionService.remove(containerId);
        return SessionResult.OK.name();
    }

    public List<Signature> getSignatures(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getSignatures().forEach(signatureWrapper -> signatures.add(transformSignature(signatureWrapper)));
        return signatures;
    }

    public Signature transformSignature(HashcodeSignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration);
        org.digidoc4j.Signature dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        signature.setId(dd4jSignature.getId());
        signature.setGeneratedSignatureId(signatureWrapper.getGeneratedSignatureId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    private DetachedDataFileContainerSessionHolder transformContainerToSession(String sessionId, DetachedDataFileContainer container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return DetachedDataFileContainerSessionHolder.builder()
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}