package ee.openeid.siga;

import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RequestTransformerTest {

    @Test
    public void transformValidSignatureToDetails_validDateFormats() throws Exception {
        Path documentPath = Paths.get(new ClassPathResource("datafile.asice").getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(inputStream).build();

        GetContainerSignatureDetailsResponse response = RequestTransformer.transformSignatureToDetails(container.getSignatures().get(0));
        Assert.assertEquals("2014-11-17T14:11:47Z", response.getClaimedSigningTime());
        Assert.assertEquals("2014-11-17T14:11:46Z", response.getOcspResponseCreationTime());
        Assert.assertEquals("2014-11-17T14:11:46Z", response.getTimeStampCreationTime());
        Assert.assertEquals("2014-11-17T14:11:46Z", response.getTrustedSigningTime());
    }

    @Test(expected = InvalidCertificateException.class)
    public void couldNotTransformAuthCertificate() throws IOException {
        Path documentPath = Paths.get(new ClassPathResource("mari-liis_auth.cer").getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));
        String authCert = IOUtils.toString(inputStream, Charset.defaultCharset());
        RequestTransformer.transformRemoteRequest(authCert, null, null, null);
    }
}
