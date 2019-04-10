package ee.openeid.siga.auth.filter.hmac;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Getter
@Builder
public class HmacSignature {
    private final static String DELIMITER = ":";
    @NonNull
    private final String macAlgorithm;
    private final String signature;
    @NonNull
    private final String serviceUuid;
    @NonNull
    private final String requestMethod;
    @NonNull
    private final String uri;
    @NonNull
    private final String timestamp;
    private final byte[] payload;

    public boolean isValid(byte[] signingSecret) throws DecoderException, NoSuchAlgorithmException, InvalidKeyException {
        requireNonNull(signingSecret, "signingSecret");
        final byte[] calculatedSignature = getSignature(signingSecret);
        return MessageDigest.isEqual(calculatedSignature, Hex.decodeHex(signature));
    }

    public String getSignature(String signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        return Hex.encodeHexString(getSignature(signingSecret.getBytes()));
    }

    private byte[] getSignature(byte[] signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac hmac = Mac.getInstance(macAlgorithm);
        SecretKeySpec secretKey = new SecretKeySpec(signingSecret, macAlgorithm);
        hmac.init(secretKey);
        hmac.update((serviceUuid + DELIMITER + timestamp + DELIMITER + requestMethod + DELIMITER + uri + DELIMITER).getBytes(UTF_8));
        hmac.update(payload);
        final byte[] signatureBytes = hmac.doFinal();
        hmac.reset();
        return signatureBytes;
    }
}
