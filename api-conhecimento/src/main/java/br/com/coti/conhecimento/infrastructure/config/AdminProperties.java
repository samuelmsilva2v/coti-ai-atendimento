package br.com.coti.conhecimento.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Token administrativo de {@code /api/conhecimentos/**}, vindo de {@code ADMIN_API_TOKEN}. */
@Validated
@ConfigurationProperties("coti.admin")
public record AdminProperties(
        @NotBlank(message = "defina ADMIN_API_TOKEN")
        @Size(min = 16, message = "ADMIN_API_TOKEN deve ter ao menos 16 caracteres")
        String token) {

    @Override
    public String toString() {
        return "AdminProperties[token=***]";
    }
}
