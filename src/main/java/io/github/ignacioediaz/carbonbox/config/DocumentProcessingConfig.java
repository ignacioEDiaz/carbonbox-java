package io.github.ignacioediaz.carbonbox.config;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.ProcessFileCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RecordProcessingFailureCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RedriveUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.FileStorage;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.UploadReadModel;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.services.CsvParser;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityDataRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowErrorRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.DatabaseMessageQueue;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.QueueWorker;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.storage.LocalFileStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Composicion: aca se conectan los casos de uso (sin dependencias de Spring) con sus adaptadores.
 * Es el unico lugar que conoce las dos capas.
 */
@Configuration
@EnableConfigurationProperties(CarbonBoxProperties.class)
public class DocumentProcessingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return new UnitOfWork() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }

    @Bean
    FileStorage fileStorage(CarbonBoxProperties props) {
        return new LocalFileStorage(Path.of(props.storage().dir()));
    }

    @Bean
    DatabaseMessageQueue messageQueue(JdbcTemplate jdbc, Clock clock) {
        return new DatabaseMessageQueue(jdbc, clock);
    }

    @Bean
    CsvParser csvParser() {
        return new CsvParser();
    }

    @Bean
    CreateUploadCommandHandler createUploadCommandHandler(UploadRepository uploads, FileStorage storage,
                                                          DatabaseMessageQueue queue, UnitOfWork uow, Clock clock,
                                                          CarbonBoxProperties props) {
        return new CreateUploadCommandHandler(uploads, storage, queue, uow, clock, props.upload().maxBytes());
    }

    @Bean
    ProcessFileCommandHandler processFileCommandHandler(UploadRepository uploads, ActivityDataRepository activity,
                                                        RowErrorRepository rowErrors, FileStorage storage,
                                                        UnitOfWork uow, CsvParser parser, Clock clock) {
        return new ProcessFileCommandHandler(uploads, activity, rowErrors, storage, uow, parser, clock);
    }

    @Bean
    RecordProcessingFailureCommandHandler recordProcessingFailureCommandHandler(UploadRepository uploads,
                                                                                UnitOfWork uow, Clock clock) {
        return new RecordProcessingFailureCommandHandler(uploads, uow, clock);
    }

    @Bean
    RedriveUploadCommandHandler redriveUploadCommandHandler(UploadRepository uploads, DatabaseMessageQueue queue,
                                                            UnitOfWork uow, Clock clock) {
        return new RedriveUploadCommandHandler(uploads, queue, uow, clock);
    }

    @Bean
    UploadQueryHandlers uploadQueryHandlers(UploadReadModel readModel) {
        return new UploadQueryHandlers(readModel);
    }

    @Bean
    QueueWorker queueWorker(DatabaseMessageQueue queue, ProcessFileCommandHandler process,
                            RecordProcessingFailureCommandHandler failures, CarbonBoxProperties props) {
        return new QueueWorker(queue, process, failures, props.queue());
    }
}
