package com.wjiamao.belt_monitor.camera;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/camera")
public class CameraStreamController {

    private int frameCount = 0;

    // ── 单帧快照（验证图像是否能显示）──
    // 浏览器访问：http://localhost:8080/api/camera/snapshot
    @GetMapping(value = "/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] snapshot() throws Exception {
        return generateFrame();
    }

    // ── MJPEG 推流 ──
    @GetMapping("/stream")
    public StreamingResponseBody stream(HttpServletResponse response) {
        response.setStatus(200);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setContentType("multipart/x-mixed-replace; boundary=frame");

        return outputStream -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] jpg = generateFrame();
                    String header =
                            "--frame\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: " + jpg.length + "\r\n\r\n";
                    outputStream.write(header.getBytes(StandardCharsets.US_ASCII));
                    outputStream.write(jpg);
                    outputStream.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    outputStream.flush();
                    Thread.sleep(40);
                    frameCount++;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ignore) {}
        };
    }

    /**
     * 生成一帧模拟皮带画面
     * ★ 相机到货后只需替换这个方法 ★
     */
    private byte[] generateFrame() throws Exception {
        int W = 640, H = 360;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 皮带背景
        g.setColor(new Color(14, 16, 9));
        g.fillRect(0, 0, W, H);

        // 运动条纹
        int stripeH = 40;
        int offset  = (frameCount * 3) % stripeH;
        g.setColor(new Color(35, 42, 18, 80));
        for (int y = -stripeH + offset; y < H + stripeH; y += stripeH) {
            g.fillRect(0, y, W, 5);
        }

        // 细横纹
        g.setColor(new Color(25, 30, 12, 40));
        for (int y = -stripeH + offset; y < H + stripeH; y += 8) {
            g.fillRect(0, y, W, 1);
        }

        // 两侧暗影
        GradientPaint leftGrad  = new GradientPaint(0, 0, new Color(0,0,0,160), 50, 0, new Color(0,0,0,0));
        GradientPaint rightGrad = new GradientPaint(W-50, 0, new Color(0,0,0,0), W, 0, new Color(0,0,0,160));
        g.setPaint(leftGrad);  g.fillRect(0,    0, 50, H);
        g.setPaint(rightGrad); g.fillRect(W-50, 0, 50, H);

        // 中心参考线
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{8, 6}, (frameCount * 2) % 14f));
        g.setColor(new Color(0, 180, 220, 30));
        g.drawLine(W/2, 0, W/2, H);

        // 煤屑污点
        g.setStroke(new BasicStroke(1));
        for (int i = 0; i < 30; i++) {
            int px = (int)((Math.sin(i * 137.5 + frameCount * 0.008) * 0.38 + 0.5) * W);
            int py = (int)(((i * 67.0 + frameCount * 2.5) % (H + 10)) - 5);
            int r  = 1 + (i % 3);
            g.setColor(new Color(8, 6, 2, 60 + (i % 5) * 20));
            g.fillOval(px - r, py - r, r*2, r*2);
        }

        // 时间戳
        g.setColor(new Color(180, 200, 210, 70));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.drawString(String.format("CAM-01  %tT", System.currentTimeMillis()), 10, H - 10);
        g.drawString("K0+080  BELT BOTTOM", W - 145, H - 10);

        // 录制标记
        g.setColor(new Color(200, 60, 60, 120));
        g.fillOval(W - 52, 8, 6, 6);
        g.setColor(new Color(180, 190, 200, 60));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.drawString("REC", W - 42, 17);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }
}