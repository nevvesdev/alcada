package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.infrastructure.http.RemoteServiceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alcada")
public record AlcadaProperties(RemoteServiceProperties bureau, RemoteServiceProperties limits) {
}