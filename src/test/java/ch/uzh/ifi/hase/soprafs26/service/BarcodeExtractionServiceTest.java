package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

class BarcodeExtractionServiceTest {

    private BarcodeExtractionService barcodeExtractionService;

    @BeforeEach
    void setUp() {
        barcodeExtractionService = new BarcodeExtractionService();
    }

    @Test
    void extractBarcode_validBarcodeImage_success() throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode("7610848492087", BarcodeFormat.EAN_13, 320, 160);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);

        MockMultipartFile image = new MockMultipartFile("image", "ean13.png", "image/png", outputStream.toByteArray());

        String extracted = barcodeExtractionService.extractBarcode(image);
        assertEquals("7610848492087", extracted);
    }

    @Test
    void extractBarcode_emptyFile_throwsBadRequest() {
        MockMultipartFile image = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> barcodeExtractionService.extractBarcode(image));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void extractBarcode_noBarcodeInImage_throwsUnprocessableEntity() throws Exception {
        BufferedImage plainImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(plainImage, "png", outputStream);

        MockMultipartFile image = new MockMultipartFile("image", "plain.png", "image/png", outputStream.toByteArray());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> barcodeExtractionService.extractBarcode(image));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    }

    @Test
    void extractBarcode_nonProductBarcodeFormat_throwsUnprocessableEntity() throws Exception {
        // QR code decoding is more stable across CI environments than CODE_128.
        // It is still a non-product barcode format and must be rejected.
        BitMatrix matrix = new MultiFormatWriter().encode("ABC-abc-1234", BarcodeFormat.QR_CODE, 320, 320);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);

        MockMultipartFile image = new MockMultipartFile("image", "code128.png", "image/png", outputStream.toByteArray());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> barcodeExtractionService.extractBarcode(image));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    }
}
