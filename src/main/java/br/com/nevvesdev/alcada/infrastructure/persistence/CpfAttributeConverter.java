package br.com.nevvesdev.alcada.infrastructure.persistence;

import br.com.nevvesdev.alcada.infrastructure.security.CpfCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter
public class CpfAttributeConverter implements AttributeConverter<String, String> {

    private final CpfCipher cipher;

    public CpfAttributeConverter(CpfCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}