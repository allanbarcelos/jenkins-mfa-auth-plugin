package io.jenkins.plugins;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.User;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@Extension
public class QrCodeAction implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(QrCodeAction.class.getName());
    private static final String ISSUER = "Jenkins";
    // Base32 alphabet: A-Z and 2-7, with optional padding
    private static final String BASE32_PATTERN = "[A-Za-z2-7]+=*";

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "MFA TOTP Action";
    }

    @Override
    public String getUrlName() {
        return "mfa-totp";
    }

    public void doGenerateSecret(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        User currentUser = User.current();
        if (currentUser == null) {
            rsp.sendError(401, "Not authenticated");
            return;
        }
        try {
            String username = currentUser.getId();
            String secret = TOTPUtil.generateSecret();
            String otpAuthUrl = TOTPUtil.getQRBarcodeURL(username, ISSUER, secret);

            String[] backupCodes = BackupCodeUtil.generateCodes();
            String[] backupHashes = BackupCodeUtil.hashCodes(backupCodes);
            // Store hashes in session so they survive form submission
            req.getSession().setAttribute(
                    MfaConstants.MFA_PENDING_BACKUP_CODES_ATTR,
                    BackupCodeUtil.toStorageString(backupHashes));

            JSONObject json = new JSONObject();
            json.put("secret", secret);
            json.put("otpAuthUrl", otpAuthUrl);
            JSONArray codesArray = new JSONArray();
            codesArray.addAll(Arrays.asList(backupCodes));
            json.put("backupCodes", codesArray);

            rsp.setContentType("application/json;charset=UTF-8");
            rsp.getWriter().print(json.toString());
        } catch (Exception e) {
            LOGGER.severe("Failed to generate secret: " + e.getMessage());
            rsp.sendError(500, "Failed to generate secret");
        }
    }

    public void doQrcode(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        String secret = req.getParameter("secret");
        if (secret == null || secret.isEmpty()) {
            rsp.sendError(400, "Missing secret parameter");
            return;
        }
        if (!secret.matches(BASE32_PATTERN)) {
            rsp.sendError(400, "Invalid secret format");
            return;
        }

        User currentUser = User.current();
        String username = currentUser != null ? currentUser.getId() : "user";
        try {
            generateQRCodeImage(rsp, TOTPUtil.getQRBarcodeURL(username, ISSUER, secret.toUpperCase()));
        } catch (Exception e) {
            LOGGER.severe("QR Code generation failed: " + e.getMessage());
            rsp.sendError(500, "Failed to generate QR Code");
        }
    }

    private void generateQRCodeImage(StaplerResponse2 rsp, String otpAuth) throws IOException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(otpAuth, BarcodeFormat.QR_CODE, 200, 200);
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        rsp.setContentType("image/png");
        javax.imageio.ImageIO.write(qrImage, "PNG", rsp.getOutputStream());
    }
}
