package io.davidarchanjo.listener;

import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.debezium.data.Envelope.FieldName.*;
import static io.debezium.data.Envelope.Operation;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Component
public class DebeziumListener {

    private final Executor executor = Executors.newSingleThreadExecutor();    
    private final DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

    public DebeziumListener(Configuration customerConnectorConfiguration) {
        this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
            .using(customerConnectorConfiguration.asProperties())
            .notifying(this::handleChangeEvent)
            .build();
    }

    private void handleChangeEvent(RecordChangeEvent<SourceRecord> sourceRecordRecordChangeEvent) {
        SourceRecord sourceRecord = sourceRecordRecordChangeEvent.record();
        log.info("Key = {}, Value = {}", sourceRecord.key(), sourceRecord.value());
        Struct sourceRecordChangeValue= (Struct) sourceRecord.value();
//        log.info("SourceRecordChangeValue = '{}'", sourceRecordChangeValue);
         if (sourceRecordChangeValue != null) {
             try {
                 Operation operation = Operation.forCode((String) sourceRecordChangeValue.get(OPERATION));

                 // Operation.READ operation events are always triggered when application initializes
                 // We're only interested in CREATE operation which are triggered upon new insert registry
                 if (operation != Operation.READ) {
                     String record =
                             operation == Operation.DELETE ? BEFORE : AFTER; // Handling Update & Insert operations.

                     Struct struct = (Struct) sourceRecordChangeValue.get(record);
                     Map<String, Object> payload = struct.schema().fields().stream()
                             .map(Field::name)
                             .filter(fieldName -> struct.get(fieldName) != null)
                             .map(fieldName -> Pair.of(fieldName, struct.get(fieldName)))
                             .collect(toMap(Pair::getKey, Pair::getValue));

//                  this.customerService.replicateData(payload, operation);
                     log.info("Updated Data: {} with Operation: {}", payload, operation.name());
//
//                     Struct sourceStruct = (Struct) sourceRecordChangeValue.get("source");
//                     log.info("{}", sourceStruct.schema().fields());
//                     Map<String, Object> payloadSource = sourceStruct.schema().fields().stream()
//                             .map(Field::name)
//                             .filter(fieldName -> struct.get(fieldName) != null)
//                             .map(fieldName -> Pair.of(fieldName, struct.get(fieldName)))
//                             .collect(toMap(Pair::getKey, Pair::getValue));
//                     log.info("Data Source info: {}", payloadSource);
                 }
             } catch (Exception e){
                 log.error(e.getMessage());
             }
         }
    }

    @PostConstruct
    private void start() {
        this.executor.execute(debeziumEngine);
    }

    @PreDestroy
    private void stop() throws IOException {
        if (Objects.nonNull(this.debeziumEngine)) {
            this.debeziumEngine.close();
        }
    }

}