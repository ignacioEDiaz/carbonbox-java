package io.github.ignacioediaz.carbonbox;

import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.QueueWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo contra la API, con H2 y el worker llamado a mano. */
@SpringBootTest(properties = {
        "carbonbox.worker.enabled=false",
        "carbonbox.storage.dir=${java.io.tmpdir}/carbonbox-test-api",
        "spring.datasource.url=jdbc:h2:mem:api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DirtiesContext
class UploadApiIntegrationTest {

    private static final String CSV = """
            category,amount,unit,date
            electricity,1250.5,kWh,2026-01-15
            electricity,100,kWh,2026-01-20
            diesel,85.2,L,2026-02-03
            waste,15,kg,2026-02-30
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    QueueWorker worker;

    private MockMultipartFile archivo(String nombre, String contenido) {
        return new MockMultipartFile("file", nombre, "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void subirProcesarYConsultar() throws Exception {
        String body = mvc.perform(multipart("/api/uploads").file(archivo("enero.csv", CSV)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.created").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/uploads/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        worker.pollOnce();

        mvc.perform(get("/api/uploads/" + id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.processedRows").value(3))
                .andExpect(jsonPath("$.errorRows").value(1));
        mvc.perform(get("/api/uploads/" + id + "/results?page=1&size=2"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/uploads/" + id + "/errors"))
                .andExpect(jsonPath("$[0].rowNumber").value(5))
                .andExpect(jsonPath("$[0].message", containsString("YYYY-MM-DD")));
        mvc.perform(get("/api/activity/by-month"))
                .andExpect(jsonPath("$[?(@.month=='2026-01' && @.category=='electricity')].total").value(1350.5));
        mvc.perform(get("/api/uploads/summary"))
                .andExpect(jsonPath("$.completed").value(1));

        // el mismo archivo otra vez: 200 con el mismo id, no se crea otro upload
        mvc.perform(multipart("/api/uploads").file(archivo("copia.csv", CSV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void erroresDeValidacion() throws Exception {
        mvc.perform(multipart("/api/uploads").file(archivo("foto.png", CSV)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(".csv")));
        mvc.perform(multipart("/api/uploads").file(archivo("x.csv", "nombre,edad\nana,3\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("encabezado")));
        mvc.perform(get("/api/uploads/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/uploads/no-es-un-id"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/uploads?page=0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"));
    }
}
