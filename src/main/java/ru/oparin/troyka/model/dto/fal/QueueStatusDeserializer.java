package ru.oparin.troyka.model.dto.fal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import ru.oparin.troyka.model.enums.QueueStatus;

import java.io.IOException;

/**
 * Десериализатор для преобразования строки в QueueStatus при чтении JSON от Fal.ai API.
 */
public class QueueStatusDeserializer extends JsonDeserializer<QueueStatus> {
    @Override
    public QueueStatus deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        return QueueStatus.fromString(value);
    }
}


