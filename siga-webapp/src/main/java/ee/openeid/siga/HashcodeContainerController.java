package ee.openeid.siga;

import ee.openeid.siga.common.DataToSignWrapper;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.SigningChallenge;
import ee.openeid.siga.common.SmartIdInformation;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RestController
public class HashcodeContainerController {

    private HashcodeContainerService containerService;
    private HashcodeContainerValidationService validationService;
    private HashcodeContainerSigningService signingService;

    @SigaEventLog(eventName = SigaEventName.HC_CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @RequestMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerResponse createContainer(@RequestBody CreateHashcodeContainerRequest createContainerRequest) {
        List<HashcodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateHashcodeDataFiles(dataFiles);

        String sessionId = containerService.createContainer(RequestTransformer.transformHashcodeDataFilesForApplication(dataFiles));
        CreateHashcodeContainerResponse response = new CreateHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_UPLOAD_CONTAINER, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @RequestMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadHashcodeContainerResponse uploadContainer(@RequestBody UploadHashcodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        RequestValidator.validateFileContent(container);

        String sessionId = containerService.uploadContainer(container);
        UploadHashcodeContainerResponse response = new UploadHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER)
    @RequestMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerValidationReportResponse validateContainer(@RequestBody CreateHashcodeContainerValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        RequestValidator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(container);
        CreateHashcodeContainerValidationReportResponse response = new CreateHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER_BY_ID)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetHashcodeContainerValidationReportResponse response = new GetHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerRemoteSigningRequest createRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateRemoteSigning(createRemoteSigningRequest.getSigningCertificate(), createRemoteSigningRequest.getSignatureProfile());

        String signingCertificate = createRemoteSigningRequest.getSigningCertificate();
        String signatureProfile = createRemoteSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createRemoteSigningRequest.getSignatureProductionPlace();
        List<String> roles = createRemoteSigningRequest.getRoles();

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(signingCertificate, signatureProfile, signatureProductionPlace, roles);
        DataToSignWrapper dataToSignWrapper = signingService.createDataToSign(containerId, signatureParameters);
        DataToSign dataToSign = dataToSignWrapper.getDataToSign();

        CreateHashcodeContainerRemoteSigningResponse response = new CreateHashcodeContainerRemoteSigningResponse();
        response.setGeneratedSignatureId(dataToSignWrapper.getGeneratedSignatureId());
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());

        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_REMOTE_SIGNING_FINISH)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    public UpdateHashcodeContainerRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId, @RequestBody UpdateHashcodeContainerRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        Result result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateHashcodeContainerRemoteSigningResponse response = new UpdateHashcodeContainerRemoteSigningResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_MOBILE_ID_SIGNING_INIT)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createMobileIdSigningRequest.getSignatureProfile());

        List<String> roles = createMobileIdSigningRequest.getRoles();
        String signatureProfile = createMobileIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createMobileIdSigningRequest.getSignatureProductionPlace();

        MobileIdInformation mobileIdInformation = getMobileIdInformation(createMobileIdSigningRequest);

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);

        SigningChallenge challenge = signingService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);

        CreateHashcodeContainerMobileIdSigningResponse response = new CreateHashcodeContainerMobileIdSigningResponse();
        response.setChallengeId(challenge.getChallengeId());
        response.setGeneratedSignatureId(challenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_MOBILE_ID_SIGNING_STATUS)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        String status = signingService.processMobileStatus(containerId, signatureId);

        GetHashcodeContainerMobileIdSigningStatusResponse response = new GetHashcodeContainerMobileIdSigningStatusResponse();
        response.setMidStatus(status);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_INIT)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/smartidsigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerSmartIdSigningResponse createHashcodeContainerSmartIdSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerSmartIdSigningRequest createSmartIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createSmartIdSigningRequest.getSignatureProfile());

        List<String> roles = createSmartIdSigningRequest.getRoles();
        String signatureProfile = createSmartIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createSmartIdSigningRequest.getSignatureProductionPlace();

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        SmartIdInformation smartIdInformation = getSmartIdInformation(createSmartIdSigningRequest);
        RequestValidator.validateSmartIdInformation(smartIdInformation);

        SigningChallenge signingChallenge = signingService.startSmartIdSigning(containerId, getSmartIdInformation(createSmartIdSigningRequest), signatureParameters);

        CreateHashcodeContainerSmartIdSigningResponse response = new CreateHashcodeContainerSmartIdSigningResponse();
        response.setChallengeId(signingChallenge.getChallengeId());
        response.setGeneratedSignatureId(signingChallenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_STATUS)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/smartidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerSmartIdSigningStatusResponse getSmartSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        SmartIdInformation smartIdInformation = RequestTransformer.transformSmartIdInformation(null, null, null);
        String status = signingService.processSmartIdStatus(containerId, signatureId, smartIdInformation);

        GetHashcodeContainerSmartIdSigningStatusResponse response = new GetHashcodeContainerSmartIdSigningStatusResponse();
        response.setSidStatus(status);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURES_LIST)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.Signature> signatures = containerService.getSignatures(containerId);
        GetHashcodeContainerSignaturesResponse response = new GetHashcodeContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignaturesForResponse(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURE)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/signatures/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerSignatureDetailsResponse getSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        org.digidoc4j.Signature signature = containerService.getSignature(containerId, signatureId);
        return RequestTransformer.transformHashcodeSignatureToDetails(signature);
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_DATAFILES_LIST)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerDataFilesResponse getDataFilesList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.HashcodeDataFile> dataFiles = containerService.getDataFiles(containerId);
        GetHashcodeContainerDataFilesResponse response = new GetHashcodeContainerDataFilesResponse();
        response.getDataFiles().addAll(RequestTransformer.transformHashcodeDataFilesForResponse(dataFiles));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_ADD_DATAFILE)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerDataFileResponse addHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerDataFileRequest containerDataFileRequest) {
        RequestValidator.validateContainerId(containerId);
        HashcodeDataFile hashcodeDataFile = containerDataFileRequest.getDataFile();
        RequestValidator.validateHashcodeDataFile(hashcodeDataFile);

        ee.openeid.siga.common.HashcodeDataFile dataFileForApplication = RequestTransformer.transformHashcodeDataFilesForApplication(Collections.singletonList(hashcodeDataFile)).get(0);
        Result result = containerService.addDataFile(containerId, dataFileForApplication);
        CreateHashcodeContainerDataFileResponse response = new CreateHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_DATAFILE)
    @RequestMapping(value = "/hashcodecontainers/{containerId}/datafiles/{datafileName}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashcodeContainerDataFileResponse deleteHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "datafileName") String datafileName) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateFileName(datafileName);

        Result result = containerService.removeDataFile(containerId, datafileName);
        DeleteHashcodeContainerDataFileResponse response = new DeleteHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.HC_GET_CONTAINER)
    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetHashcodeContainerResponse response = new GetHashcodeContainerResponse();
        response.setContainer(container);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_CONTAINER)
    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashcodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        Result result = containerService.closeSession(containerId);
        DeleteHashcodeContainerResponse response = new DeleteHashcodeContainerResponse();
        response.setResult(result.name());
        return response;
    }

    private MobileIdInformation getMobileIdInformation(CreateHashcodeContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        String language = createMobileIdSigningRequest.getLanguage();
        String messageToDisplay = createMobileIdSigningRequest.getMessageToDisplay();
        String phoneNo = createMobileIdSigningRequest.getPhoneNo();
        String personIdentifier = createMobileIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformMobileIdInformation(language, messageToDisplay, personIdentifier, phoneNo);
    }

    private SmartIdInformation getSmartIdInformation(CreateHashcodeContainerSmartIdSigningRequest containerSmartIdSigningRequest) {
        String country = containerSmartIdSigningRequest.getCountry();
        String messageToDisplay = containerSmartIdSigningRequest.getMessageToDisplay();
        String personIdentifier = containerSmartIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformSmartIdInformation(country, messageToDisplay, personIdentifier);

    }

    @Autowired
    protected void setContainerService(HashcodeContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    protected void setValidationService(HashcodeContainerValidationService validationService) {
        this.validationService = validationService;
    }

    @Autowired
    public void setSigningService(HashcodeContainerSigningService signingService) {
        this.signingService = signingService;
    }
}