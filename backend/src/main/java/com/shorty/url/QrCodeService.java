package com.shorty.url;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.shorty.api.ApiException;
import com.shorty.config.AppProperties;
import java.io.ByteArrayOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

    private final AppProperties props;

    public QrCodeService(AppProperties props) {
        this.props = props;
    }

    public byte[] pngFor(String code) {
        String url = props.baseUrl().replaceAll("/$", "") + "/s/" + ShortCodes.normalize(code);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "qr_failed", "Could not generate QR code");
        }
    }
}
