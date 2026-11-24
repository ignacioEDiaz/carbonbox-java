package io.github.ignacioediaz.carbonbox.documentprocessing.application;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.services.CsvParser;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowError;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();
    private final UUID uploadId = UUID.randomUUID();

    private CsvParser.ParseResult parse(String csv) {
        return parser.parse(uploadId, csv.getBytes(StandardCharsets.UTF_8)).getRight();
    }

    @Test
    void leeFilasValidas() {
        var r = parse("category,amount,unit,date\nelectricity,1250.5,kWh,2026-01-15\ngas,3,m3,2026-02-01\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.rows()).hasSize(2);
        var primera = r.rows().get(0);
        assertThat(primera.category()).isEqualTo("electricity");
        assertThat(primera.amount()).isEqualByComparingTo(new BigDecimal("1250.5"));
        assertThat(primera.date()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(primera.rowNumber()).isEqualTo(2);
    }

    @Test
    void aceptaBomDeExcelSaltosDeWindowsYMayusculasEnElEncabezado() {
        var r = parse("﻿Category,Amount,Unit,Date\r\nwater,42,m3,2026-03-10\r\n");
        assertThat(r.rows()).hasSize(1);
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void unaFilaMalaNoFrenaElArchivoYDiceElMotivo() {
        var r = parse("""
                category,amount,unit,date
                electricity,-40,kWh,2026-03-01
                ,15,kg,2026-03-05
                waste,15,kg,2026-02-30
                water,abc,m3,2026-03-10
                diesel,10,L
                ok,1,kg,2026-01-01
                """);
        assertThat(r.rows()).extracting(x -> x.category()).containsExactly("ok");
        assertThat(r.errors()).extracting(RowError::rowNumber).containsExactly(2, 3, 4, 5, 6);
        assertThat(r.errors().get(0).message()).contains("positivo");
        assertThat(r.errors().get(1).message()).contains("category");
        assertThat(r.errors().get(2).message()).contains("2026-02-30"); // fecha que no existe
        assertThat(r.errors().get(3).message()).contains("no es un número");
        assertThat(r.errors().get(4).message()).contains("columnas");
    }

    @Test
    void respetaComasDentroDeComillas() {
        var r = parse("category,amount,unit,date\n\"business_travel, flights\",2400,km,2026-02-21\n");
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).category()).isEqualTo("business_travel, flights");
    }

    @Test
    void ignoraLineasEnBlanco() {
        var r = parse("category,amount,unit,date\n\nx,1,kg,2026-01-01\n   \n");
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).rowNumber()).isEqualTo(3);
    }

    @Test
    void rechazaUnEncabezadoDistinto() {
        var r = parser.parse(uploadId, "fecha,monto\n2026-01-01,3".getBytes(StandardCharsets.UTF_8));
        assertThat(r.isLeft()).isTrue();
        assertThat(r.getLeft().message()).contains("category,amount,unit,date");
    }
}
