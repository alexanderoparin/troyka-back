package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Сервис для сжатия изображений перед отправкой в провайдеры генерации.
 * Используется для соблюдения лимитов размера запросов (например, Gemini API: 7MB на файл, 20MB на запрос).
 */
@Slf4j
@Service
public class ImageCompressionService {

    /**
     * Максимальный размер одного изображения в байтах (7MB для Gemini API).
     */
    private static final long MAX_SINGLE_IMAGE_SIZE = 7 * 1024 * 1024;

    /**
     * Целевой размер после сжатия (6.5MB с запасом).
     */
    private static final long TARGET_IMAGE_SIZE = (long) (6.5 * 1024 * 1024);

    /**
     * Максимальное разрешение для уменьшения (2048px по большей стороне).
     */
    private static final int MAX_DIMENSION = 2048;

    /**
     * Минимальное качество JPEG (0.6).
     */
    private static final float MIN_JPEG_QUALITY = 0.6f;

    /**
     * Начальное качество JPEG для сжатия (0.85).
     */
    private static final float INITIAL_JPEG_QUALITY = 0.85f;

    /**
     * Сжать изображение до целевого размера.
     * Если изображение уже меньше лимита, возвращается без изменений.
     *
     * @param imageBytes исходные байты изображения
     * @param mimeType   MIME тип изображения (image/jpeg, image/png)
     * @return сжатые байты изображения
     * @throws IOException если произошла ошибка при обработке изображения
     */
    public byte[] compressImage(byte[] imageBytes, String mimeType) throws IOException {
        if (imageBytes.length <= MAX_SINGLE_IMAGE_SIZE) {
            log.debug("Изображение уже соответствует лимиту ({} bytes), сжатие не требуется", imageBytes.length);
            return imageBytes;
        }

        log.info("Начало сжатия изображения: размер={} bytes, mimeType={}", imageBytes.length, mimeType);

        // Читаем изображение
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Не удалось прочитать изображение");
        }

        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        log.debug("Исходные размеры изображения: {}x{}", originalWidth, originalHeight);

        // Конвертируем в RGB если нужно (для PNG с прозрачностью)
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = rgbImage;
        }

        // Шаг 1: Уменьшаем разрешение, если нужно
        if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
            image = resizeImage(image, MAX_DIMENSION);
            log.debug("Изображение уменьшено до: {}x{}", image.getWidth(), image.getHeight());
        }

        // Шаг 2: Сжимаем через JPEG quality
        byte[] compressed = compressWithQuality(image, INITIAL_JPEG_QUALITY);

        // Шаг 3: Если все еще слишком большое, уменьшаем quality
        if (compressed.length > TARGET_IMAGE_SIZE) {
            log.debug("Изображение все еще слишком большое ({} bytes), уменьшаем quality", compressed.length);
            float quality = INITIAL_JPEG_QUALITY;
            float step = 0.05f;

            while (compressed.length > TARGET_IMAGE_SIZE && quality >= MIN_JPEG_QUALITY) {
                quality -= step;
                compressed = compressWithQuality(image, quality);
                log.debug("Попытка сжатия с quality={}, размер={} bytes", quality, compressed.length);
            }

            // Если все еще не помещается, уменьшаем разрешение еще больше
            if (compressed.length > TARGET_IMAGE_SIZE) {
                log.warn("Изображение все еще слишком большое после сжатия quality, уменьшаем разрешение");
                int newMaxDimension = (int) (MAX_DIMENSION * 0.75);
                image = resizeImage(image, newMaxDimension);
                compressed = compressWithQuality(image, MIN_JPEG_QUALITY);
            }
        }

        float compressionRatio = (float) compressed.length / imageBytes.length;
        log.info("Сжатие завершено: исходный размер={} bytes, сжатый размер={} bytes, коэффициент={:.2f}",
                imageBytes.length, compressed.length, compressionRatio);

        return compressed;
    }

    /**
     * Изменить размер изображения, сохраняя пропорции.
     *
     * @param image        исходное изображение
     * @param maxDimension максимальный размер по большей стороне
     * @return измененное изображение
     */
    private BufferedImage resizeImage(BufferedImage image, int maxDimension) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return image;
        }

        double scale;
        if (width > height) {
            scale = (double) maxDimension / width;
        } else {
            scale = (double) maxDimension / height;
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * Сжать изображение с указанным качеством JPEG.
     *
     * @param image  изображение
     * @param quality качество (0.0 - 1.0)
     * @return сжатые байты
     * @throws IOException если произошла ошибка
     */
    private byte[] compressWithQuality(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Используем ImageWriter для контроля качества
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();

        return baos.toByteArray();
    }
}
