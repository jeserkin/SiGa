package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerMobileIdSigningStatusResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class MobileSigningAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signWithMidSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void signWithMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response responseSigningDelay5s = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        Response responseSigningDelay7s = postMidSigningInSession(flow, midSigningRequestWithDefault("60001018800", "+37200000566", "LT"));

        String signatureId5s = responseSigningDelay5s.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId7s = responseSigningDelay7s.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        await().atMost(16, SECONDS).with().pollInterval(5, SECONDS).until(() -> "SIGNATURE".equals(pollForMidSigning(flow, signatureId5s).body().as(GetContainerMobileIdSigningStatusResponse.class).getMidStatus()));
        await().atMost(16, SECONDS).with().pollInterval(5, SECONDS).until(() -> "SIGNATURE".equals(pollForMidSigning(flow, signatureId7s).body().as(GetContainerMobileIdSigningStatusResponse.class).getMidStatus()));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    public void mobileIdNotActivated() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019928", "+37200000366", "LT"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void mobileIdCertificateRevoked() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019939", "+37200000266", "LT"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void mobileIdSendingFailed() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019947", "+37207110066", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SENDING_ERROR);
    }


    @Test
    public void mobileIdUserCancel() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, USER_CANCEL);
    }

    @Test
    public void mobileIdSignatureNotValid() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019961", "+37200000666", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, NOT_VALID);
    }

    @Test
    public void mobileIdSimError() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019972", "+37201200266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SIM_ERROR);
    }

    @Test
    public void mobileIdPhoneNotInNetwork() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019983", "+37213100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, PHONE_ABSENT);
    }

    @Test
    public void mobileIdUserTimeout() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, EXPIRED_TRANSACTION);
    }

    @Test
    public void mobileIdUserCancelAndRetries() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void mobileIdUserTimeoutsAndRetries() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void missingPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("", "+37200000766", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidPersonIdentifierFormatReturnsSoapErrorFromDds() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("P!NO-23a.31,23", "+37200000766", "LT"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void missingPhoneNumber() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidPhoneNumberFormat() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "-/ssasa", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void missingLanguageInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidLanguageInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "SOM", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_LANGUAGE);
    }

    @Test
    public void missingProfileInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidProfileInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "T", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void maximumDataInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "LT", "message", "Tallinn", "Harjumaa", "75544", "Estonia", "I hava a role"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void midStatusRequestForOtherUserContainer() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        response = getMidSigningInSession(flow, signatureId);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}