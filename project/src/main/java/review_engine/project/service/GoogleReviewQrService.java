package review_engine.project.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import review_engine.project.entity.Business;
import review_engine.project.exception.ApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Generates a small on-demand QR image containing only the owner's configured Google review URL.
 */
@Service
public class GoogleReviewQrService {
    private static final int SIZE = 400;

    public byte[] generate(Business business) {
        String url = GoogleReviewUrlValidator.validate(business.getGoogleReviewUrl());
        if (url == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "A Google review URL has not been configured.");
        }
        try {
            var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 4));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", output)) {
                throw new IllegalStateException("PNG encoding is unavailable.");
            }
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Google review QR code could not be generated.", exception);
        }
    }
}
